-- CreateSchema
CREATE SCHEMA IF NOT EXISTS "forecasting";

-- CreateSchema
CREATE SCHEMA IF NOT EXISTS "public";

-- CreateEnum
CREATE TYPE "public"."Role" AS ENUM ('SUPER_ADMIN', 'ADMIN', 'CLIENT');

-- CreateEnum
CREATE TYPE "public"."MarketType" AS ENUM ('DAM', 'GDAM', 'RTM', 'REC');

-- CreateEnum
CREATE TYPE "public"."DatasetStatus" AS ENUM ('ACTIVE', 'REPLACED', 'DELETED');

-- CreateEnum
CREATE TYPE "public"."UploadAction" AS ENUM ('UPLOAD', 'REPLACE', 'DELETE');

-- CreateEnum
CREATE TYPE "public"."state_ut_enum" AS ENUM ('state', 'ut');

-- CreateTable
CREATE TABLE "public"."User" (
    "id" TEXT NOT NULL,
    "username" VARCHAR(50) NOT NULL,
    "email" VARCHAR(255) NOT NULL,
    "passwordHash" VARCHAR(255) NOT NULL,
    "role" "public"."Role" NOT NULL DEFAULT 'CLIENT',
    "hiddenModules" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    "readOnlyModules" TEXT[] DEFAULT ARRAY[]::TEXT[],

    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."Dataset" (
    "id" TEXT NOT NULL,
    "market" "public"."MarketType" NOT NULL,
    "deliveryDate" DATE NOT NULL,
    "fileName" VARCHAR(255) NOT NULL,
    "status" "public"."DatasetStatus" NOT NULL DEFAULT 'ACTIVE',
    "uploadedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Dataset_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."UploadHistory" (
    "id" TEXT NOT NULL,
    "datasetId" TEXT NOT NULL,
    "market" "public"."MarketType" NOT NULL,
    "deliveryDate" DATE NOT NULL,
    "action" "public"."UploadAction" NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "UploadHistory_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."DamRecord" (
    "id" TEXT NOT NULL,
    "datasetId" TEXT NOT NULL,
    "intervalNumber" SMALLINT NOT NULL,
    "intervalTime" VARCHAR(15) NOT NULL,
    "purchaseBid" DECIMAL(18,4) NOT NULL,
    "sellBid" DECIMAL(18,4) NOT NULL,
    "mcv" DECIMAL(18,4) NOT NULL,
    "fsv" DECIMAL(18,4) NOT NULL,
    "mcp" DECIMAL(18,4) NOT NULL,
    "date" VARCHAR(10),

    CONSTRAINT "DamRecord_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."GdamRecord" (
    "id" TEXT NOT NULL,
    "datasetId" TEXT NOT NULL,
    "intervalNumber" SMALLINT NOT NULL,
    "intervalTime" VARCHAR(15) NOT NULL,
    "purchaseBid" DECIMAL(18,4) NOT NULL,
    "sellBidTotal" DECIMAL(18,4) NOT NULL,
    "sellBidSolar" DECIMAL(18,4),
    "sellBidNonSolar" DECIMAL(18,4),
    "sellBidHydro" DECIMAL(18,4) NOT NULL,
    "mcvTotal" DECIMAL(18,4) NOT NULL,
    "mcvSolar" DECIMAL(18,4),
    "mcvNonSolar" DECIMAL(18,4),
    "mcvHydro" DECIMAL(18,4) NOT NULL,
    "fsvTotal" DECIMAL(18,4) NOT NULL,
    "fsvSolar" DECIMAL(18,4),
    "fsvNonSolar" DECIMAL(18,4),
    "fsvHydro" DECIMAL(18,4) NOT NULL,
    "mcp" DECIMAL(18,4) NOT NULL,
    "date" VARCHAR(10),
    "fsvORE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "fsvOtherRE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "fsvWind" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "mcvORE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "mcvOtherRE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "mcvWind" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "sellBidORE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "sellBidOtherRE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "sellBidWind" DECIMAL(18,4) NOT NULL DEFAULT 0,

    CONSTRAINT "GdamRecord_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."GdamNewRecord" (
    "id" TEXT NOT NULL,
    "datasetId" TEXT NOT NULL,
    "date" VARCHAR(10),
    "intervalNumber" SMALLINT NOT NULL,
    "intervalTime" VARCHAR(15) NOT NULL,
    "purchaseBid" DECIMAL(18,4) NOT NULL,
    "sellBidTotal" DECIMAL(18,4) NOT NULL,
    "sellBidHydro" DECIMAL(18,4) NOT NULL,
    "sellBidWind" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "sellBidOtherRE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "sellBidDRE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "mcvTotal" DECIMAL(18,4) NOT NULL,
    "mcvHydro" DECIMAL(18,4) NOT NULL,
    "mcvWind" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "mcvOtherRE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "mcvDRE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "fsvTotal" DECIMAL(18,4) NOT NULL,
    "fsvHydro" DECIMAL(18,4) NOT NULL,
    "fsvWind" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "fsvOtherRE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "fsvDRE" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "mcp" DECIMAL(18,4) NOT NULL,

    CONSTRAINT "GdamNewRecord_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."RtmRecord" (
    "id" TEXT NOT NULL,
    "datasetId" TEXT NOT NULL,
    "intervalNumber" SMALLINT NOT NULL,
    "intervalTime" VARCHAR(15) NOT NULL,
    "sessionId" VARCHAR(50) NOT NULL,
    "purchaseBid" DECIMAL(18,4) NOT NULL,
    "sellBid" DECIMAL(18,4) NOT NULL,
    "mcv" DECIMAL(18,4) NOT NULL,
    "fsv" DECIMAL(18,4) NOT NULL,
    "mcp" DECIMAL(18,4) NOT NULL,
    "date" VARCHAR(10),

    CONSTRAINT "RtmRecord_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."RecRecord" (
    "id" TEXT NOT NULL,
    "datasetId" TEXT NOT NULL,
    "buyBids" DECIMAL(18,4) NOT NULL,
    "clearedPrice" DECIMAL(18,4) NOT NULL,
    "clearedVolume" DECIMAL(18,4) NOT NULL,
    "month" VARCHAR(50) NOT NULL,
    "noOfParticipants" INTEGER NOT NULL,
    "sellBids" DECIMAL(18,4) NOT NULL,
    "type" VARCHAR(50) NOT NULL,
    "year" SMALLINT NOT NULL,

    CONSTRAINT "RecRecord_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."region_state" (
    "id" SERIAL NOT NULL,
    "regional_grid" VARCHAR(30),
    "region_code" VARCHAR(10),
    "region_name" VARCHAR(50),
    "state_name" VARCHAR(50) NOT NULL,
    "state_code" VARCHAR(10),
    "state_or_ut" "public"."state_ut_enum",
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "region_state_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."discom_list" (
    "code" VARCHAR(20) NOT NULL,
    "legal_name" VARCHAR(255) NOT NULL,
    "state_code" VARCHAR(100),
    "discom_type" VARCHAR(30),
    "id" SERIAL NOT NULL,
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "discom_list_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."ists_charges" (
    "id" SERIAL NOT NULL,
    "ists_loss_percent" DECIMAL(6,4) NOT NULL,
    "end_date" DATE NOT NULL,
    "start_date" DATE NOT NULL,
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ists_charges_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."iex_fees" (
    "id" SERIAL NOT NULL,
    "month" INTEGER,
    "exchange_fees" DECIMAL(12,4),
    "exchange_fees_gst" DECIMAL(12,4),
    "nldc_application_fees" DECIMAL(12,4),
    "nldc_scheduling_fees" DECIMAL(12,4),
    "sldc_scheduling_fees" DECIMAL(12,4),
    "other_fix_charges" DECIMAL(12,4),
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "iex_fees_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."prolt_margin" (
    "month" INTEGER NOT NULL,
    "customer_id" VARCHAR(50) NOT NULL,
    "trading_margin" DECIMAL(12,4),
    "trading_margin_gst" DECIMAL(12,4),
    "prolt_margin" DECIMAL(12,4),
    "prolt_margin_gst" DECIMAL(12,4),
    "id" SERIAL NOT NULL,
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "prolt_margin_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."ctu_charges" (
    "id" SERIAL NOT NULL,
    "state" VARCHAR(50) NOT NULL,
    "month" INTEGER NOT NULL,
    "ctu_charges_rs_per_kwh" DECIMAL(12,6),
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ctu_charges_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."state_charges" (
    "id" SERIAL NOT NULL,
    "state" VARCHAR(100) NOT NULL,
    "category" VARCHAR(100),
    "sub_category" VARCHAR(255),
    "supply_voltage_category" VARCHAR(100),
    "voltage_level" VARCHAR(100),
    "from_date" DATE NOT NULL,
    "to_date" DATE NOT NULL,
    "demand_fixed_charge_kva_per_month_rs" DECIMAL(12,6),
    "cross_subsidy" DECIMAL(12,6),
    "distribution_wheeling_charges" DECIMAL(12,6),
    "stu_charges" DECIMAL(12,6),
    "stu_loss_percent" DECIMAL(12,6),
    "wheeling_loss_percent" DECIMAL(12,6),
    "additional_charge" DECIMAL(12,6),
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "discom" VARCHAR(100),

    CONSTRAINT "state_charges_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."state_tariff" (
    "month" INTEGER NOT NULL,
    "state" VARCHAR(100) NOT NULL,
    "sub_category" VARCHAR(255) NOT NULL,
    "id" SERIAL NOT NULL,
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "base_energy_rate" DECIMAL(12,6) NOT NULL,
    "base_energy_unit" VARCHAR(10) NOT NULL,
    "consumer_category" VARCHAR(50) NOT NULL,
    "energy_rate" DECIMAL(12,6) NOT NULL,
    "supply_voltage" VARCHAR(50) NOT NULL,
    "supply_voltage_category" VARCHAR(100) NOT NULL,
    "tod_charge_percent" INTEGER NOT NULL,
    "tod_end_time" VARCHAR(20) NOT NULL,
    "tod_start_time" VARCHAR(20) NOT NULL,
    "discom" VARCHAR(100),
    "consumption_month" INTEGER,

    CONSTRAINT "state_tariff_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."user_details" (
    "customer_id" VARCHAR(50) NOT NULL,
    "address_line1" VARCHAR(255),
    "address_line2" VARCHAR(255),
    "address_pin_code" INTEGER,
    "address_state" VARCHAR(100),
    "billing_address_line1" VARCHAR(255),
    "billing_address_line2" VARCHAR(255),
    "billing_pin_code" INTEGER,
    "billing_state" VARCHAR(100),
    "contact_person_name" VARCHAR(100),
    "contact_email" VARCHAR(255),
    "contact_phone" VARCHAR(20),
    "contact_whatsapp_number" VARCHAR(20),
    "contact_alternate_phone" VARCHAR(20),
    "factory_name" VARCHAR(255),
    "gst_number" VARCHAR(30),
    "sanctioned_load_kw" DECIMAL(12,2),
    "discom" VARCHAR(20),
    "state" VARCHAR(100),
    "supply_voltage" VARCHAR(10),
    "category" VARCHAR(10),
    "sub_category" VARCHAR(10),

    CONSTRAINT "user_details_pkey" PRIMARY KEY ("customer_id")
);

-- CreateTable
CREATE TABLE "public"."StateDemandData" (
    "id" TEXT NOT NULL,
    "stateName" TEXT NOT NULL,
    "date" VARCHAR(10) NOT NULL,
    "timeStr" VARCHAR(10) NOT NULL,
    "demand" INTEGER NOT NULL,
    "unit" TEXT NOT NULL,
    "region" TEXT NOT NULL,
    "price" DOUBLE PRECISION NOT NULL,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "StateDemandData_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."WeatherForecast" (
    "id" TEXT NOT NULL,
    "date" VARCHAR(10) NOT NULL,
    "maxTemp" DOUBLE PRECISION NOT NULL,
    "minTemp" DOUBLE PRECISION NOT NULL,
    "windSpeed" DOUBLE PRECISION NOT NULL,
    "relativeHumidity" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "precipitationProb" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "precipitationSum" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "sunshineDuration" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "sunrise" VARCHAR(10) NOT NULL DEFAULT '',
    "sunset" VARCHAR(10) NOT NULL DEFAULT '',
    "isActual" BOOLEAN NOT NULL DEFAULT false,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "cityId" TEXT NOT NULL,
    "rainSum" DOUBLE PRECISION NOT NULL DEFAULT 0,

    CONSTRAINT "WeatherForecast_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."WeatherForecastHourly" (
    "id" TEXT NOT NULL,
    "date" VARCHAR(10) NOT NULL,
    "timeStr" VARCHAR(5) NOT NULL,
    "maxTemp" DOUBLE PRECISION NOT NULL,
    "minTemp" DOUBLE PRECISION NOT NULL,
    "windSpeed" DOUBLE PRECISION NOT NULL,
    "relativeHumidity" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "precipitationProb" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "precipitationSum" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "sunshineDuration" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "sunrise" VARCHAR(10) NOT NULL DEFAULT '',
    "sunset" VARCHAR(10) NOT NULL DEFAULT '',
    "isActual" BOOLEAN NOT NULL DEFAULT false,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "cityId" TEXT NOT NULL,
    "rainSum" DOUBLE PRECISION NOT NULL DEFAULT 0,

    CONSTRAINT "WeatherForecastHourly_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."WeatherHistorical" (
    "id" TEXT NOT NULL,
    "date" VARCHAR(10) NOT NULL,
    "maxTemp" DOUBLE PRECISION NOT NULL,
    "minTemp" DOUBLE PRECISION NOT NULL,
    "windSpeed" DOUBLE PRECISION NOT NULL,
    "relativeHumidity" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "precipitationProb" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "precipitationSum" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "sunshineDuration" DOUBLE PRECISION NOT NULL DEFAULT 0,
    "sunrise" VARCHAR(10) NOT NULL DEFAULT '',
    "sunset" VARCHAR(10) NOT NULL DEFAULT '',
    "isActual" BOOLEAN NOT NULL DEFAULT false,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "cityId" TEXT NOT NULL,
    "rainSum" DOUBLE PRECISION NOT NULL DEFAULT 0,

    CONSTRAINT "WeatherHistorical_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."NppRawDemandData" (
    "id" TEXT NOT NULL,
    "date" TEXT NOT NULL,
    "timeStr" TEXT NOT NULL,
    "demandMet" DOUBLE PRECISION NOT NULL,
    "dataUpdatedAt" TEXT,
    "fetchedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NppRawDemandData_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."NppRawGenerationData" (
    "id" TEXT NOT NULL,
    "date" TEXT NOT NULL,
    "timeStr" TEXT NOT NULL,
    "thermal" DOUBLE PRECISION,
    "gas" DOUBLE PRECISION,
    "nuclear" DOUBLE PRECISION,
    "hydro" DOUBLE PRECISION,
    "wind" DOUBLE PRECISION,
    "solar" DOUBLE PRECISION,
    "dataUpdatedAt" TEXT,
    "fetchedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NppRawGenerationData_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."NppAdjustedDemandData" (
    "id" TEXT NOT NULL,
    "date" TEXT NOT NULL,
    "timeStr" TEXT NOT NULL,
    "demandMet" DOUBLE PRECISION NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NppAdjustedDemandData_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."NppAdjustedGenerationData" (
    "id" TEXT NOT NULL,
    "date" TEXT NOT NULL,
    "timeStr" TEXT NOT NULL,
    "thermal" DOUBLE PRECISION,
    "gas" DOUBLE PRECISION,
    "nuclear" DOUBLE PRECISION,
    "hydro" DOUBLE PRECISION,
    "wind" DOUBLE PRECISION,
    "solar" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NppAdjustedGenerationData_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."HolidayCalendar" (
    "id" TEXT NOT NULL,
    "month" VARCHAR(20) NOT NULL,
    "holidayDate" VARCHAR(10) NOT NULL,
    "holidayName" VARCHAR(255) NOT NULL,
    "holidayType" VARCHAR(50) NOT NULL,
    "state" VARCHAR(100) NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "HolidayCalendar_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."ApiLog" (
    "id" TEXT NOT NULL,
    "apiName" VARCHAR(100) NOT NULL,
    "endpoint" VARCHAR(255),
    "status" VARCHAR(50) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "details" TEXT,

    CONSTRAINT "ApiLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "forecasting"."all_india_demand_forecasting_v2" (
    "timestamp" TIMESTAMP(3) NOT NULL,
    "forecast_demand" DOUBLE PRECISION,

    CONSTRAINT "all_india_demand_forecasting_v2_pkey" PRIMARY KEY ("timestamp")
);

-- CreateTable
CREATE TABLE "forecasting"."Generation_forecasting" (
    "id" SERIAL NOT NULL,
    "forecast_generated_at" TIMESTAMP(3),
    "forecast_for_timestamp" TIMESTAMP(3),
    "source" VARCHAR(50),
    "forecast_mw" DOUBLE PRECISION,
    "model_name" VARCHAR(100),
    "model_version" VARCHAR(50),

    CONSTRAINT "Generation_forecasting_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."market_operation" (
    "id" TEXT NOT NULL,
    "date" DATE NOT NULL,
    "timeblock" INTEGER NOT NULL,
    "dam_mcp" DECIMAL(10,4) NOT NULL,
    "rtm_mcp" DECIMAL(10,4) NOT NULL,
    "gdam_mcp" DECIMAL(10,4) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "market_operation_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."CityStateData" (
    "id" TEXT NOT NULL,
    "cityName" VARCHAR(100) NOT NULL,
    "stateName" VARCHAR(100) NOT NULL,
    "population" INTEGER NOT NULL,
    "latitude" DOUBLE PRECISION NOT NULL,
    "longitude" DOUBLE PRECISION NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CityStateData_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."savings_calculator_entries" (
    "id" TEXT NOT NULL,
    "client_name" VARCHAR(255) NOT NULL,
    "industry_name" VARCHAR(255) NOT NULL,
    "address" TEXT NOT NULL,
    "sanctioned_load_kw" DECIMAL(12,4),
    "state_code" VARCHAR(100),
    "discom" VARCHAR(100),
    "consumer_category" VARCHAR(100),
    "voltage_level" VARCHAR(50),
    "tod_consumptions" JSONB,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    "prolt_margin" DECIMAL(12,4),
    "trader_margin" DECIMAL(12,4),
    "apply_electricity_duty" BOOLEAN NOT NULL DEFAULT true,
    "arrear_amount" DECIMAL(15,2),
    "bill_date" VARCHAR(50),
    "billed_demand_kv" DECIMAL(12,4),
    "consultancy_fee" DECIMAL(12,4),
    "current_lpsc" DECIMAL(15,2),
    "power_factor" DECIMAL(5,4),
    "probus_platform_fee" DECIMAL(12,4),
    "created_by" VARCHAR(50),
    "updated_by" VARCHAR(50),
    "metering_charges" DECIMAL(12,4),

    CONSTRAINT "savings_calculator_entries_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."savings_calculator_entry_histories" (
    "id" TEXT NOT NULL,
    "entry_id" VARCHAR(36) NOT NULL,
    "version" INTEGER NOT NULL,
    "client_name" VARCHAR(255) NOT NULL,
    "industry_name" VARCHAR(255) NOT NULL,
    "address" TEXT NOT NULL,
    "sanctioned_load_kw" DECIMAL(12,4),
    "prolt_margin" DECIMAL(12,4),
    "trader_margin" DECIMAL(12,4),
    "consultancy_fee" DECIMAL(12,4),
    "probus_platform_fee" DECIMAL(12,4),
    "state_code" VARCHAR(100),
    "discom" VARCHAR(100),
    "consumer_category" VARCHAR(100),
    "voltage_level" VARCHAR(50),
    "tod_consumptions" JSONB,
    "billed_demand_kv" DECIMAL(12,4),
    "power_factor" DECIMAL(5,4),
    "arrear_amount" DECIMAL(15,2),
    "current_lpsc" DECIMAL(15,2),
    "bill_date" VARCHAR(50),
    "apply_electricity_duty" BOOLEAN NOT NULL DEFAULT true,
    "changed_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "created_by" VARCHAR(50),
    "updated_by" VARCHAR(50),
    "metering_charges" DECIMAL(12,4),

    CONSTRAINT "savings_calculator_entry_histories_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."savings_calculator_new_entries" (
    "id" TEXT NOT NULL,
    "client_name" VARCHAR(255) NOT NULL,
    "industry_name" VARCHAR(255) NOT NULL,
    "address" TEXT NOT NULL,
    "sanctioned_load_kw" DECIMAL(12,4),
    "prolt_margin" DECIMAL(12,4),
    "trader_margin" DECIMAL(12,4),
    "metering_charges" DECIMAL(12,4),
    "consultancy_fee" DECIMAL(12,4),
    "probus_platform_fee" DECIMAL(12,4),
    "state_code" VARCHAR(100),
    "discom" VARCHAR(100),
    "consumer_category" VARCHAR(100),
    "voltage_level" VARCHAR(50),
    "tod_consumptions" JSONB,
    "billed_demand_kv" DECIMAL(12,4),
    "power_factor" DECIMAL(5,4),
    "arrear_amount" DECIMAL(15,2),
    "current_lpsc" DECIMAL(15,2),
    "bill_date" VARCHAR(50),
    "apply_electricity_duty" BOOLEAN NOT NULL DEFAULT true,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    "createdBy" VARCHAR(50),
    "updatedBy" VARCHAR(50),

    CONSTRAINT "savings_calculator_new_entries_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."savings_calculator_new_entry_histories" (
    "id" TEXT NOT NULL,
    "entry_id" VARCHAR(36) NOT NULL,
    "version" INTEGER NOT NULL,
    "client_name" VARCHAR(255) NOT NULL,
    "industry_name" VARCHAR(255) NOT NULL,
    "address" TEXT NOT NULL,
    "sanctioned_load_kw" DECIMAL(12,4),
    "prolt_margin" DECIMAL(12,4),
    "trader_margin" DECIMAL(12,4),
    "metering_charges" DECIMAL(12,4),
    "consultancy_fee" DECIMAL(12,4),
    "probus_platform_fee" DECIMAL(12,4),
    "state_code" VARCHAR(100),
    "discom" VARCHAR(100),
    "consumer_category" VARCHAR(100),
    "voltage_level" VARCHAR(50),
    "tod_consumptions" JSONB,
    "billed_demand_kv" DECIMAL(12,4),
    "power_factor" DECIMAL(5,4),
    "arrear_amount" DECIMAL(15,2),
    "current_lpsc" DECIMAL(15,2),
    "bill_date" VARCHAR(50),
    "apply_electricity_duty" BOOLEAN NOT NULL DEFAULT true,
    "changed_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" VARCHAR(50),
    "updatedBy" VARCHAR(50),

    CONSTRAINT "savings_calculator_new_entry_histories_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."StateMarketRecord" (
    "id" TEXT NOT NULL,
    "market" "public"."MarketType" NOT NULL,
    "area" VARCHAR(100) NOT NULL,
    "deliveryDate" DATE NOT NULL,
    "intervalNumber" SMALLINT NOT NULL,
    "intervalTime" VARCHAR(15) NOT NULL,
    "purchaseBid" DECIMAL(18,4) NOT NULL,
    "sellBid" DECIMAL(18,4) NOT NULL,
    "clearedVolume" DECIMAL(18,4) NOT NULL,
    "price" DECIMAL(18,4) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "StateMarketRecord_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."fppa_charges" (
    "id" SERIAL NOT NULL,
    "state" VARCHAR(100) NOT NULL,
    "month" INTEGER NOT NULL,
    "fppa_charge_percent" DECIMAL(12,6),
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "discom" VARCHAR(200),
    "billing_month" INTEGER,

    CONSTRAINT "fppa_charges_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."AuditLog" (
    "id" TEXT NOT NULL,
    "tableName" VARCHAR(100) NOT NULL,
    "recordId" VARCHAR(100) NOT NULL,
    "action" VARCHAR(50) NOT NULL,
    "oldData" JSONB,
    "newData" JSONB,
    "userId" VARCHAR(100),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "AuditLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "forecasting"."gdam_forecasting" (
    "id" TEXT NOT NULL,
    "date" VARCHAR(10) NOT NULL DEFAULT '',
    "intervalNumber" SMALLINT NOT NULL DEFAULT 0,
    "intervalTime" VARCHAR(15) NOT NULL DEFAULT '',
    "purchaseBid" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "sellBid" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "mcv" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "fsv" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "mcp" DECIMAL(18,4) NOT NULL DEFAULT 0,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "gdam_forecasting_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "forecasting"."dam_forecasting" (
    "forecasting_for" TIMESTAMP(3) NOT NULL,
    "time_block" SMALLINT NOT NULL,
    "model_number" INTEGER NOT NULL,
    "forecast_date" TIMESTAMP(3) NOT NULL,
    "predicted_mcp" DECIMAL(18,4) NOT NULL,
    "confidence_pct" DECIMAL(5,2),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "id" TEXT NOT NULL,
    "price_range" VARCHAR(50),

    CONSTRAINT "dam_forecasting_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "forecasting"."rtm_dayahead" (
    "id" TEXT NOT NULL,
    "date" VARCHAR(10) NOT NULL,
    "intervalNumber" SMALLINT NOT NULL,
    "intervalTime" VARCHAR(15) NOT NULL,
    "forecastingFor" DATE NOT NULL,
    "forecastDate" TIMESTAMP(6) NOT NULL,
    "predictedMcp" DECIMAL(18,4) NOT NULL,
    "predictedMcpP10" DECIMAL(18,4),
    "predictedMcpP50" DECIMAL(18,4),
    "predictedMcpP90" DECIMAL(18,4),
    "confidence" DECIMAL(18,4),
    "price_range" VARCHAR(50),
    "modelVersion" VARCHAR(50),
    "modelNumber" INTEGER NOT NULL DEFAULT 1,
    "createdAt" TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(6) NOT NULL,

    CONSTRAINT "rtm_dayahead_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "forecasting"."rtm_nowcast" (
    "id" TEXT NOT NULL,
    "date" VARCHAR(10) NOT NULL,
    "intervalNumber" SMALLINT NOT NULL,
    "intervalTime" VARCHAR(15) NOT NULL,
    "forecastedAt" TIMESTAMP(6) NOT NULL,
    "currentBlock" SMALLINT,
    "predictedMcp" DECIMAL(18,4) NOT NULL,
    "predictedMcpP10" DECIMAL(18,4),
    "predictedMcpP50" DECIMAL(18,4),
    "predictedMcpP90" DECIMAL(18,4),
    "confidence" DECIMAL(18,4),
    "price_range" VARCHAR(50),
    "modelVersion" VARCHAR(50),
    "createdAt" TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(6) NOT NULL,

    CONSTRAINT "rtm_nowcast_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "forecasting"."consumer_demand_forecasting" (
    "timestamp" TIMESTAMP(3) NOT NULL,
    "slot_number" SMALLINT NOT NULL,
    "actual_energy" DECIMAL(18,4),
    "forecasted_energy" DECIMAL(18,4),
    "confidence" DECIMAL(18,4),
    "meter_id" VARCHAR(50) NOT NULL DEFAULT 'X2521837'
);

-- CreateTable
CREATE TABLE "forecasting"."all_india_demand_forecasting" (
    "id" TEXT NOT NULL,
    "date" VARCHAR(10) NOT NULL,
    "hour" VARCHAR(5) NOT NULL,
    "intervalNumber" SMALLINT NOT NULL,
    "timeBlock" VARCHAR(15) NOT NULL,
    "forecastedDemand" DECIMAL(18,4) NOT NULL,
    "actualDemand" DECIMAL(18,4),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "all_india_demand_forecasting_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."UserAuditLog" (
    "id" TEXT NOT NULL,
    "action" VARCHAR(50) NOT NULL,
    "targetUserId" VARCHAR(36),
    "targetUser" VARCHAR(50) NOT NULL,
    "performedBy" VARCHAR(50) NOT NULL,
    "changes" JSONB NOT NULL,
    "timestamp" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "UserAuditLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "forecasting"."market_forecasting" (
    "date" VARCHAR(10) NOT NULL,
    "slots" JSONB NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "market_forecasting_pkey" PRIMARY KEY ("date")
);

-- CreateTable
CREATE TABLE "forecasting"."gdam_dayahead_forecast" (
    "id" TEXT NOT NULL,
    "date" VARCHAR(10) NOT NULL,
    "intervalNumber" SMALLINT NOT NULL,
    "intervalTime" VARCHAR(15) NOT NULL,
    "forecastingFor" DATE,
    "forecastDate" TIMESTAMP(3),
    "predictedMcp" DECIMAL(18,4),
    "predictedMcpP10" DECIMAL(18,4),
    "predictedMcpP50" DECIMAL(18,4),
    "predictedMcpP90" DECIMAL(18,4),
    "confidence" DECIMAL(18,4),
    "price_range" VARCHAR(30),
    "modelVersion" VARCHAR(100),
    "modelNumber" INTEGER DEFAULT 1,
    "createdAt" TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "gdam_dayahead_forecast_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."trader_performance_entries" (
    "id" TEXT NOT NULL,
    "client_name" VARCHAR(255) NOT NULL,
    "industry_name" VARCHAR(255) NOT NULL,
    "address" TEXT NOT NULL,
    "sanctioned_load_kw" DECIMAL(12,4),
    "prolt_margin" DECIMAL(12,4),
    "trader_margin" DECIMAL(12,4),
    "metering_charges" DECIMAL(12,4),
    "consultancy_fee" DECIMAL(12,4),
    "probus_platform_fee" DECIMAL(12,4),
    "state_code" VARCHAR(100),
    "discom" VARCHAR(100),
    "consumer_category" VARCHAR(100),
    "voltage_level" VARCHAR(50),
    "tod_consumptions" JSONB,
    "billed_demand_kv" DECIMAL(12,4),
    "power_factor" DECIMAL(5,4),
    "arrear_amount" DECIMAL(15,2),
    "current_lpsc" DECIMAL(15,2),
    "bill_date" VARCHAR(50),
    "apply_electricity_duty" BOOLEAN NOT NULL DEFAULT true,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    "createdBy" VARCHAR(50),
    "updatedBy" VARCHAR(50),

    CONSTRAINT "trader_performance_entries_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."trader_performance_entry_histories" (
    "id" TEXT NOT NULL,
    "entry_id" VARCHAR(36) NOT NULL,
    "version" INTEGER NOT NULL,
    "client_name" VARCHAR(255) NOT NULL,
    "industry_name" VARCHAR(255) NOT NULL,
    "address" TEXT NOT NULL,
    "sanctioned_load_kw" DECIMAL(12,4),
    "prolt_margin" DECIMAL(12,4),
    "trader_margin" DECIMAL(12,4),
    "metering_charges" DECIMAL(12,4),
    "consultancy_fee" DECIMAL(12,4),
    "probus_platform_fee" DECIMAL(12,4),
    "state_code" VARCHAR(100),
    "discom" VARCHAR(100),
    "consumer_category" VARCHAR(100),
    "voltage_level" VARCHAR(50),
    "tod_consumptions" JSONB,
    "billed_demand_kv" DECIMAL(12,4),
    "power_factor" DECIMAL(5,4),
    "arrear_amount" DECIMAL(15,2),
    "current_lpsc" DECIMAL(15,2),
    "bill_date" VARCHAR(50),
    "apply_electricity_duty" BOOLEAN NOT NULL DEFAULT true,
    "changed_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" VARCHAR(50),
    "updatedBy" VARCHAR(50),

    CONSTRAINT "trader_performance_entry_histories_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."trader_performance_actual_entries" (
    "id" TEXT NOT NULL,
    "client_name" VARCHAR(255) NOT NULL,
    "industry_name" VARCHAR(255) NOT NULL,
    "address" TEXT NOT NULL,
    "sanctioned_load_kw" DECIMAL(12,4),
    "prolt_margin" DECIMAL(12,4),
    "trader_margin" DECIMAL(12,4),
    "metering_charges" DECIMAL(12,4),
    "consultancy_fee" DECIMAL(12,4),
    "probus_platform_fee" DECIMAL(12,4),
    "state_code" VARCHAR(100),
    "discom" VARCHAR(100),
    "consumer_category" VARCHAR(100),
    "voltage_level" VARCHAR(50),
    "tod_consumptions" JSONB,
    "billed_demand_kv" DECIMAL(12,4),
    "power_factor" DECIMAL(5,4),
    "arrear_amount" DECIMAL(15,2),
    "current_lpsc" DECIMAL(15,2),
    "bill_date" VARCHAR(50),
    "apply_electricity_duty" BOOLEAN NOT NULL DEFAULT true,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,
    "createdBy" VARCHAR(50),
    "updatedBy" VARCHAR(50),

    CONSTRAINT "trader_performance_actual_entries_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."trader_performance_actual_entry_histories" (
    "id" TEXT NOT NULL,
    "entry_id" VARCHAR(36) NOT NULL,
    "version" INTEGER NOT NULL,
    "client_name" VARCHAR(255) NOT NULL,
    "industry_name" VARCHAR(255) NOT NULL,
    "address" TEXT NOT NULL,
    "sanctioned_load_kw" DECIMAL(12,4),
    "prolt_margin" DECIMAL(12,4),
    "trader_margin" DECIMAL(12,4),
    "metering_charges" DECIMAL(12,4),
    "consultancy_fee" DECIMAL(12,4),
    "probus_platform_fee" DECIMAL(12,4),
    "state_code" VARCHAR(100),
    "discom" VARCHAR(100),
    "consumer_category" VARCHAR(100),
    "voltage_level" VARCHAR(50),
    "tod_consumptions" JSONB,
    "billed_demand_kv" DECIMAL(12,4),
    "power_factor" DECIMAL(5,4),
    "arrear_amount" DECIMAL(15,2),
    "current_lpsc" DECIMAL(15,2),
    "bill_date" VARCHAR(50),
    "apply_electricity_duty" BOOLEAN NOT NULL DEFAULT true,
    "changed_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "createdBy" VARCHAR(50),
    "updatedBy" VARCHAR(50),

    CONSTRAINT "trader_performance_actual_entry_histories_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."historical_weather" (
    "id" SERIAL NOT NULL,
    "timestamp" TIMESTAMPTZ(6) NOT NULL,
    "interval_number" INTEGER NOT NULL,
    "temperature" DECIMAL(10,4),
    "humidity" DECIMAL(10,4),
    "wind_speed" DECIMAL(10,4),
    "solar_radiation" DECIMAL(10,4),
    "precipitation" DECIMAL(10,4),
    "cloud_cover" DECIMAL(10,4),
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "historical_weather_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "forecasting"."GdamMcpForecast" (
    "id" SERIAL NOT NULL,
    "date" DATE NOT NULL,
    "interval_number" INTEGER NOT NULL,
    "interval_time" VARCHAR(20) NOT NULL,
    "forecast_mcp" DECIMAL(10,2) NOT NULL,
    "confidence" DECIMAL(5,2) DEFAULT 90.92,
    "price_range" VARCHAR(50),
    "model_number" INTEGER DEFAULT 1,
    "model_version" VARCHAR(50) DEFAULT 'v3_catboost',
    "forecasting_date" DATE,
    "forecast_date" DATE,
    "forecasting_for" DATE,
    "created_at" TIMESTAMPTZ(6) DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "GdamMcpForecast_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."GdamMcpForecast" (
    "id" SERIAL NOT NULL,
    "date" DATE NOT NULL,
    "interval_number" INTEGER NOT NULL,
    "interval_time" VARCHAR(20) NOT NULL,
    "forecast_mcp" DECIMAL(10,2) NOT NULL,
    "confidence" DECIMAL(5,2) DEFAULT 90.92,
    "price_range" VARCHAR(50),
    "model_number" INTEGER DEFAULT 1,
    "model_version" VARCHAR(50) DEFAULT 'v3_catboost',
    "forecasting_date" DATE,
    "forecast_date" DATE,
    "forecasting_for" DATE,
    "created_at" TIMESTAMPTZ(6) DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "GdamMcpForecast_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."exchange_dam_rate" (
    "id" SERIAL NOT NULL,
    "date" DATE NOT NULL,
    "intervalTime" TIME NOT NULL,
    "mcp" DECIMAL(10,2),
    "mcv" DECIMAL(15,2),
    "purchaseBid" DECIMAL(15,2),
    "sellBid" DECIMAL(15,2),
    "state" VARCHAR(50) NOT NULL,
    "updated_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "exchange_dam_rate_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."exchange_rtm_rate" (
    "id" SERIAL NOT NULL,
    "date" DATE NOT NULL,
    "intervalTime" TIME NOT NULL,
    "mcp" DECIMAL(10,2),
    "mcv" DECIMAL(15,2),
    "purchaseBid" DECIMAL(15,2),
    "sellBid" DECIMAL(15,2),
    "state" VARCHAR(50) NOT NULL,
    "updated_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "exchange_rtm_rate_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."exchange_gdam_rate" (
    "id" SERIAL NOT NULL,
    "date" DATE NOT NULL,
    "intervalTime" TIME NOT NULL,
    "mcp" DECIMAL(10,2),
    "mcv" DECIMAL(15,2),
    "purchaseBid" DECIMAL(15,2),
    "sellBid" DECIMAL(15,2),
    "state" VARCHAR(50) NOT NULL,
    "updated_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "exchange_gdam_rate_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "public"."dsm_charges" (
    "id" SERIAL NOT NULL,
    "state" VARCHAR(100) NOT NULL,
    "month" INTEGER NOT NULL,
    "dsm_charges_rs_per_kwh" DECIMAL(12,6),
    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "dsm_charges_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "User_username_key" ON "public"."User"("username");

-- CreateIndex
CREATE UNIQUE INDEX "User_email_key" ON "public"."User"("email");

-- CreateIndex
CREATE INDEX "User_username_idx" ON "public"."User"("username");

-- CreateIndex
CREATE INDEX "User_email_idx" ON "public"."User"("email");

-- CreateIndex
CREATE INDEX "Dataset_market_idx" ON "public"."Dataset"("market");

-- CreateIndex
CREATE INDEX "Dataset_deliveryDate_idx" ON "public"."Dataset"("deliveryDate");

-- CreateIndex
CREATE INDEX "Dataset_status_idx" ON "public"."Dataset"("status");

-- CreateIndex
CREATE INDEX "Dataset_market_deliveryDate_idx" ON "public"."Dataset"("market", "deliveryDate");

-- CreateIndex
CREATE INDEX "UploadHistory_datasetId_idx" ON "public"."UploadHistory"("datasetId");

-- CreateIndex
CREATE INDEX "UploadHistory_market_idx" ON "public"."UploadHistory"("market");

-- CreateIndex
CREATE INDEX "UploadHistory_deliveryDate_idx" ON "public"."UploadHistory"("deliveryDate");

-- CreateIndex
CREATE INDEX "UploadHistory_action_idx" ON "public"."UploadHistory"("action");

-- CreateIndex
CREATE INDEX "UploadHistory_timestamp_idx" ON "public"."UploadHistory"("timestamp");

-- CreateIndex
CREATE INDEX "DamRecord_datasetId_idx" ON "public"."DamRecord"("datasetId");

-- CreateIndex
CREATE INDEX "DamRecord_intervalNumber_idx" ON "public"."DamRecord"("intervalNumber");

-- CreateIndex
CREATE INDEX "DamRecord_intervalTime_idx" ON "public"."DamRecord"("intervalTime");

-- CreateIndex
CREATE UNIQUE INDEX "DamRecord_datasetId_intervalNumber_key" ON "public"."DamRecord"("datasetId", "intervalNumber");

-- CreateIndex
CREATE INDEX "GdamRecord_datasetId_idx" ON "public"."GdamRecord"("datasetId");

-- CreateIndex
CREATE INDEX "GdamRecord_intervalNumber_idx" ON "public"."GdamRecord"("intervalNumber");

-- CreateIndex
CREATE INDEX "GdamRecord_intervalTime_idx" ON "public"."GdamRecord"("intervalTime");

-- CreateIndex
CREATE UNIQUE INDEX "GdamRecord_datasetId_intervalNumber_key" ON "public"."GdamRecord"("datasetId", "intervalNumber");

-- CreateIndex
CREATE INDEX "GdamNewRecord_datasetId_idx" ON "public"."GdamNewRecord"("datasetId");

-- CreateIndex
CREATE INDEX "GdamNewRecord_intervalNumber_idx" ON "public"."GdamNewRecord"("intervalNumber");

-- CreateIndex
CREATE INDEX "GdamNewRecord_intervalTime_idx" ON "public"."GdamNewRecord"("intervalTime");

-- CreateIndex
CREATE UNIQUE INDEX "GdamNewRecord_datasetId_intervalNumber_key" ON "public"."GdamNewRecord"("datasetId", "intervalNumber");

-- CreateIndex
CREATE INDEX "RtmRecord_datasetId_idx" ON "public"."RtmRecord"("datasetId");

-- CreateIndex
CREATE INDEX "RtmRecord_intervalNumber_idx" ON "public"."RtmRecord"("intervalNumber");

-- CreateIndex
CREATE INDEX "RtmRecord_intervalTime_idx" ON "public"."RtmRecord"("intervalTime");

-- CreateIndex
CREATE UNIQUE INDEX "RtmRecord_datasetId_intervalNumber_key" ON "public"."RtmRecord"("datasetId", "intervalNumber");

-- CreateIndex
CREATE INDEX "RecRecord_datasetId_idx" ON "public"."RecRecord"("datasetId");

-- CreateIndex
CREATE UNIQUE INDEX "RecRecord_datasetId_year_month_type_key" ON "public"."RecRecord"("datasetId", "year", "month", "type");

-- CreateIndex
CREATE UNIQUE INDEX "uq_region_state_code" ON "public"."region_state"("state_code");

-- CreateIndex
CREATE UNIQUE INDEX "uq_discom_list_code" ON "public"."discom_list"("code");

-- CreateIndex
CREATE UNIQUE INDEX "uq_discom_list_name" ON "public"."discom_list"("legal_name");

-- CreateIndex
CREATE UNIQUE INDEX "uq_ists_charges_dates" ON "public"."ists_charges"("start_date", "end_date");

-- CreateIndex
CREATE UNIQUE INDEX "uq_iex_fees_month" ON "public"."iex_fees"("month");

-- CreateIndex
CREATE UNIQUE INDEX "uq_prolt_margin_month_cust" ON "public"."prolt_margin"("month", "customer_id");

-- CreateIndex
CREATE UNIQUE INDEX "uq_ctu_charges_state_month" ON "public"."ctu_charges"("state", "month");

-- CreateIndex
CREATE UNIQUE INDEX "uq_state_tariff_composite" ON "public"."state_tariff"("state", "consumer_category", "sub_category", "supply_voltage_category", "supply_voltage", "month", "tod_start_time", "tod_end_time");

-- CreateIndex
CREATE UNIQUE INDEX "StateDemandData_stateName_date_timeStr_key" ON "public"."StateDemandData"("stateName", "date", "timeStr");

-- CreateIndex
CREATE UNIQUE INDEX "WeatherForecast_date_cityId_key" ON "public"."WeatherForecast"("date", "cityId");

-- CreateIndex
CREATE UNIQUE INDEX "WeatherForecastHourly_date_timeStr_cityId_key" ON "public"."WeatherForecastHourly"("date", "timeStr", "cityId");

-- CreateIndex
CREATE UNIQUE INDEX "WeatherHistorical_date_cityId_key" ON "public"."WeatherHistorical"("date", "cityId");

-- CreateIndex
CREATE INDEX "NppRawDemandData_date_idx" ON "public"."NppRawDemandData"("date");

-- CreateIndex
CREATE UNIQUE INDEX "uq_npp_date_time" ON "public"."NppRawDemandData"("date", "timeStr");

-- CreateIndex
CREATE INDEX "NppRawGenerationData_date_idx" ON "public"."NppRawGenerationData"("date");

-- CreateIndex
CREATE UNIQUE INDEX "uq_npp_gen_date_time" ON "public"."NppRawGenerationData"("date", "timeStr");

-- CreateIndex
CREATE INDEX "NppAdjustedDemandData_date_idx" ON "public"."NppAdjustedDemandData"("date");

-- CreateIndex
CREATE UNIQUE INDEX "uq_npp_adj_demand" ON "public"."NppAdjustedDemandData"("date", "timeStr");

-- CreateIndex
CREATE INDEX "NppAdjustedGenerationData_date_idx" ON "public"."NppAdjustedGenerationData"("date");

-- CreateIndex
CREATE UNIQUE INDEX "uq_npp_adj_gen" ON "public"."NppAdjustedGenerationData"("date", "timeStr");

-- CreateIndex
CREATE UNIQUE INDEX "uq_market_op_date_timeblock" ON "public"."market_operation"("date", "timeblock");

-- CreateIndex
CREATE UNIQUE INDEX "uq_city_state_name" ON "public"."CityStateData"("cityName", "stateName");

-- CreateIndex
CREATE INDEX "savings_calculator_entry_histories_entry_id_idx" ON "public"."savings_calculator_entry_histories"("entry_id");

-- CreateIndex
CREATE INDEX "savings_calculator_new_entry_histories_entry_id_idx" ON "public"."savings_calculator_new_entry_histories"("entry_id");

-- CreateIndex
CREATE INDEX "StateMarketRecord_market_idx" ON "public"."StateMarketRecord"("market");

-- CreateIndex
CREATE INDEX "StateMarketRecord_area_idx" ON "public"."StateMarketRecord"("area");

-- CreateIndex
CREATE INDEX "StateMarketRecord_deliveryDate_idx" ON "public"."StateMarketRecord"("deliveryDate");

-- CreateIndex
CREATE UNIQUE INDEX "StateMarketRecord_market_area_deliveryDate_intervalNumber_key" ON "public"."StateMarketRecord"("market", "area", "deliveryDate", "intervalNumber");

-- CreateIndex
CREATE UNIQUE INDEX "uq_fppa_charges_state_discom_month" ON "public"."fppa_charges"("state", "discom", "month");

-- CreateIndex
CREATE INDEX "AuditLog_tableName_idx" ON "public"."AuditLog"("tableName");

-- CreateIndex
CREATE INDEX "AuditLog_recordId_idx" ON "public"."AuditLog"("recordId");

-- CreateIndex
CREATE INDEX "AuditLog_action_idx" ON "public"."AuditLog"("action");

-- CreateIndex
CREATE INDEX "AuditLog_createdAt_idx" ON "public"."AuditLog"("createdAt");

-- CreateIndex
CREATE INDEX "gdam_forecasting_date_idx" ON "forecasting"."gdam_forecasting"("date");

-- CreateIndex
CREATE INDEX "gdam_forecasting_intervalNumber_idx" ON "forecasting"."gdam_forecasting"("intervalNumber");

-- CreateIndex
CREATE INDEX "gdam_forecasting_intervalTime_idx" ON "forecasting"."gdam_forecasting"("intervalTime");

-- CreateIndex
CREATE INDEX "dam_forecasting_forecasting_for_idx" ON "forecasting"."dam_forecasting"("forecasting_for");

-- CreateIndex
CREATE INDEX "dam_forecasting_time_block_idx" ON "forecasting"."dam_forecasting"("time_block");

-- CreateIndex
CREATE UNIQUE INDEX "dam_forecasting_forecasting_for_time_block_model_number_for_key" ON "forecasting"."dam_forecasting"("forecasting_for", "time_block", "model_number", "forecast_date");

-- CreateIndex
CREATE UNIQUE INDEX "rtm_dayahead_date_block_uidx" ON "forecasting"."rtm_dayahead"("date", "intervalNumber");

-- CreateIndex
CREATE INDEX "rtm_nowcast_date_block_idx" ON "forecasting"."rtm_nowcast"("date", "intervalNumber", "forecastedAt" DESC);

-- CreateIndex
CREATE INDEX "idx_cdf_meter_timestamp" ON "forecasting"."consumer_demand_forecasting"("meter_id", "timestamp");

-- CreateIndex
CREATE UNIQUE INDEX "uq_consumer_demand_meter_timestamp" ON "forecasting"."consumer_demand_forecasting"("meter_id", "timestamp");

-- CreateIndex
CREATE INDEX "all_india_demand_forecasting_date_idx" ON "forecasting"."all_india_demand_forecasting"("date");

-- CreateIndex
CREATE INDEX "all_india_demand_forecasting_intervalNumber_idx" ON "forecasting"."all_india_demand_forecasting"("intervalNumber");

-- CreateIndex
CREATE INDEX "all_india_demand_forecasting_timeBlock_idx" ON "forecasting"."all_india_demand_forecasting"("timeBlock");

-- CreateIndex
CREATE UNIQUE INDEX "all_india_demand_forecasting_date_intervalNumber_key" ON "forecasting"."all_india_demand_forecasting"("date", "intervalNumber");

-- CreateIndex
CREATE INDEX "UserAuditLog_targetUserId_idx" ON "public"."UserAuditLog"("targetUserId");

-- CreateIndex
CREATE INDEX "UserAuditLog_performedBy_idx" ON "public"."UserAuditLog"("performedBy");

-- CreateIndex
CREATE INDEX "UserAuditLog_timestamp_idx" ON "public"."UserAuditLog"("timestamp");

-- CreateIndex
CREATE INDEX "trader_performance_entry_histories_entry_id_idx" ON "public"."trader_performance_entry_histories"("entry_id");

-- CreateIndex
CREATE INDEX "trader_performance_actual_entry_histories_entry_id_idx" ON "public"."trader_performance_actual_entry_histories"("entry_id");

-- CreateIndex
CREATE UNIQUE INDEX "historical_weather_timestamp_key" ON "public"."historical_weather"("timestamp");

-- CreateIndex
CREATE INDEX "historical_weather_timestamp_idx" ON "public"."historical_weather"("timestamp");

-- CreateIndex
CREATE INDEX "idx_gdam_forecast_date_fc" ON "forecasting"."GdamMcpForecast"("date");

-- CreateIndex
CREATE INDEX "idx_gdam_forecast_date_fc_dev" ON "forecasting"."GdamMcpForecast"("date");

-- CreateIndex
CREATE UNIQUE INDEX "unique_gdam_model_slot_forecasting" ON "forecasting"."GdamMcpForecast"("date", "interval_number", "model_number");

-- CreateIndex
CREATE INDEX "idx_gdam_forecast_date_pub" ON "public"."GdamMcpForecast"("date");

-- CreateIndex
CREATE INDEX "idx_gdam_forecast_date_pub_dev" ON "public"."GdamMcpForecast"("date");

-- CreateIndex
CREATE UNIQUE INDEX "unique_gdam_model_slot_public" ON "public"."GdamMcpForecast"("date", "interval_number", "model_number");

-- CreateIndex
CREATE UNIQUE INDEX "exchange_dam_rate_date_intervalTime_state_key" ON "public"."exchange_dam_rate"("date", "intervalTime", "state");

-- CreateIndex
CREATE UNIQUE INDEX "exchange_rtm_rate_date_intervalTime_state_key" ON "public"."exchange_rtm_rate"("date", "intervalTime", "state");

-- CreateIndex
CREATE UNIQUE INDEX "exchange_gdam_rate_date_intervalTime_state_key" ON "public"."exchange_gdam_rate"("date", "intervalTime", "state");

-- CreateIndex
CREATE UNIQUE INDEX "uq_dsm_charges_state_month" ON "public"."dsm_charges"("state", "month");

-- AddForeignKey
ALTER TABLE "public"."UploadHistory" ADD CONSTRAINT "UploadHistory_datasetId_fkey" FOREIGN KEY ("datasetId") REFERENCES "public"."Dataset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."DamRecord" ADD CONSTRAINT "DamRecord_datasetId_fkey" FOREIGN KEY ("datasetId") REFERENCES "public"."Dataset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."GdamRecord" ADD CONSTRAINT "GdamRecord_datasetId_fkey" FOREIGN KEY ("datasetId") REFERENCES "public"."Dataset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."GdamNewRecord" ADD CONSTRAINT "GdamNewRecord_datasetId_fkey" FOREIGN KEY ("datasetId") REFERENCES "public"."Dataset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."RtmRecord" ADD CONSTRAINT "RtmRecord_datasetId_fkey" FOREIGN KEY ("datasetId") REFERENCES "public"."Dataset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."RecRecord" ADD CONSTRAINT "RecRecord_datasetId_fkey" FOREIGN KEY ("datasetId") REFERENCES "public"."Dataset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."WeatherForecast" ADD CONSTRAINT "WeatherForecast_cityId_fkey" FOREIGN KEY ("cityId") REFERENCES "public"."CityStateData"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."WeatherForecastHourly" ADD CONSTRAINT "WeatherForecastHourly_cityId_fkey" FOREIGN KEY ("cityId") REFERENCES "public"."CityStateData"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."WeatherHistorical" ADD CONSTRAINT "WeatherHistorical_cityId_fkey" FOREIGN KEY ("cityId") REFERENCES "public"."CityStateData"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."savings_calculator_entry_histories" ADD CONSTRAINT "savings_calculator_entry_histories_entry_id_fkey" FOREIGN KEY ("entry_id") REFERENCES "public"."savings_calculator_entries"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."savings_calculator_new_entry_histories" ADD CONSTRAINT "savings_calculator_new_entry_histories_entry_id_fkey" FOREIGN KEY ("entry_id") REFERENCES "public"."savings_calculator_new_entries"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."trader_performance_entry_histories" ADD CONSTRAINT "trader_performance_entry_histories_entry_id_fkey" FOREIGN KEY ("entry_id") REFERENCES "public"."trader_performance_entries"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "public"."trader_performance_actual_entry_histories" ADD CONSTRAINT "trader_performance_actual_entry_histories_entry_id_fkey" FOREIGN KEY ("entry_id") REFERENCES "public"."trader_performance_actual_entries"("id") ON DELETE CASCADE ON UPDATE CASCADE;

