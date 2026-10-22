-- V5: Seed default admin user, roles & reference tables (from backend/prisma/seed.ts)
INSERT INTO "public"."User" ("id", "username", "email", "passwordHash", "role", "hiddenModules", "readOnlyModules", "createdAt", "updatedAt")
VALUES 
    ('c1f7a6b2-8419-4b2a-8921-274e1d1f0340', 'admin', 'admin@iexdashboard.local', '$2a$10$ZIjskOOICYsMX.vWy6f0V.I5GjW25i9FnzSmAuFkgyvyUakVaHXBu', 'ADMIN', ARRAY[]::TEXT[], ARRAY[]::TEXT[], NOW(), NOW()),
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'superadmin', 'super.admin@probus.io', '$2a$10$ZIjskOOICYsMX.vWy6f0V.I5GjW25i9FnzSmAuFkgyvyUakVaHXBu', 'SUPER_ADMIN', ARRAY[]::TEXT[], ARRAY[]::TEXT[], NOW(), NOW()),
    ('b2ccbd88-8b1a-4de7-aa5c-5aa8ac270b22', 'client', 'client@iexdashboard.local', '$2a$10$gAKYRDUOgXuS8N6plTIig.KwqYm.Gyv38gqlEoIsMObtIhbjA95fW', 'CLIENT', ARRAY[]::TEXT[], ARRAY[]::TEXT[], NOW(), NOW())
ON CONFLICT ("username") DO NOTHING;
