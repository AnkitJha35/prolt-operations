-- Migration: V9__normalize_usernames_and_backfill_full_name.sql
-- Description: Normalize approved usernames to lowercase with underscores and backfill fullName

DO $$
DECLARE
    v_matched_count INT;
    v_unexpected_fullname INT;
    v_collision_count INT;
    v_internal_collision INT;
    v_updated_rows INT;
    v_verification_errors INT;
BEGIN
    -- 1. Create temporary table with approved mapping
    CREATE TEMP TABLE _approved_user_mapping (
        source_username VARCHAR(50) PRIMARY KEY,
        target_username VARCHAR(50) NOT NULL,
        target_fullname VARCHAR(100) NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO _approved_user_mapping (source_username, target_username, target_fullname) VALUES
        ('superadmin',    'superadmin',    'System Administrator'),
        ('Yash',          'yash',          'Yash'),
        ('Saikat',        'saikat',        'Saikat'),
        ('Priyanka',      'priyanka',      'Priyanka'),
        ('harshit',       'harshit',       'Harshit'),
        ('ankit_vaish',   'ankit_vaish',   'Ankit Vaish'),
        ('Gaurav',        'gaurav',        'Gaurav'),
        ('Ankit_Jha',     'ankit_jha',     'Ankit Jha'),
        ('Neeraj Sharma', 'neeraj_sharma', 'Neeraj Sharma'),
        ('Anurag Shukla', 'anurag_shukla', 'Anurag Shukla'),
        ('Anand',         'anand',         'Anand'),
        ('Saurabh Anoop', 'saurabh_anoop', 'Saurabh Anoop');

    -- 2. Identify existing accounts in public."User" matching approved source usernames (case-sensitive)
    CREATE TEMP TABLE _matched_users ON COMMIT DROP AS
    SELECT u.id, u.username AS current_username, u."fullName" AS current_fullname,
           m.source_username, m.target_username, m.target_fullname
    FROM public."User" u
    JOIN _approved_user_mapping m ON u.username = m.source_username;

    SELECT COUNT(*) INTO v_matched_count FROM _matched_users;

    -- If no mapped users exist in this environment (e.g. empty or fresh database), complete safely
    IF v_matched_count = 0 THEN
        RAISE NOTICE 'Flyway migration V9: No matching approved accounts found in public."User". Skipping backfill.';
        RETURN;
    END IF;

    -- 3. Preflight check: Do not overwrite unexpected non-null fullName on mapped accounts
    SELECT COUNT(*) INTO v_unexpected_fullname
    FROM _matched_users mu
    WHERE mu.current_fullname IS NOT NULL;

    IF v_unexpected_fullname > 0 THEN
        RAISE EXCEPTION 'Flyway migration V9 aborted: Found % mapped user(s) with pre-existing non-null fullName. Manual investigation required.', v_unexpected_fullname;
    END IF;

    -- 4. Preflight check: Target username collisions against accounts NOT part of this mapping
    -- Checks against uq_user_username_lower_trim
    SELECT COUNT(*) INTO v_collision_count
    FROM _matched_users mu
    JOIN public."User" other ON other.id <> mu.id
    WHERE LOWER(TRIM(other.username)) = LOWER(TRIM(mu.target_username));

    IF v_collision_count > 0 THEN
        RAISE EXCEPTION 'Flyway migration V9 aborted: Target username collision detected for % account(s) under uq_user_username_lower_trim.', v_collision_count;
    END IF;

    -- 5. Preflight check: Internal collision among mapped target usernames
    SELECT COUNT(*) INTO v_internal_collision
    FROM (
        SELECT LOWER(TRIM(target_username))
        FROM _matched_users
        GROUP BY LOWER(TRIM(target_username))
        HAVING COUNT(*) > 1
    ) c;

    IF v_internal_collision > 0 THEN
        RAISE EXCEPTION 'Flyway migration V9 aborted: Internal target username collision detected within mapped accounts.';
    END IF;

    -- 6. Execute atomic update for all matched accounts
    UPDATE public."User" u
    SET username   = mu.target_username,
        "fullName" = mu.target_fullname
    FROM _matched_users mu
    WHERE u.id = mu.id;

    GET DIAGNOSTICS v_updated_rows = ROW_COUNT;

    IF v_updated_rows <> v_matched_count THEN
        RAISE EXCEPTION 'Flyway migration V9 aborted: Expected % updated rows, but updated %.', v_matched_count, v_updated_rows;
    END IF;

    -- 7. Postflight verification: Ensure all matched accounts have expected username and fullName
    SELECT COUNT(*) INTO v_verification_errors
    FROM public."User" u
    JOIN _matched_users mu ON u.id = mu.id
    WHERE u.username <> mu.target_username
       OR u."fullName" <> mu.target_fullname;

    IF v_verification_errors > 0 THEN
        RAISE EXCEPTION 'Flyway migration V9 postflight failed: % account(s) did not match expected target username or fullName after update.', v_verification_errors;
    END IF;

    RAISE NOTICE 'Flyway migration V9 completed successfully: % account(s) normalized and fullNames backfilled.', v_updated_rows;
END $$;
