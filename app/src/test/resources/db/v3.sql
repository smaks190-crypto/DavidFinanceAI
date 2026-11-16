-- Reconstructed v3 input from v4 entities and the inverse of MIGRATION_3_4.
-- No original v3 schema snapshot exists in git; this is not a device database.
CREATE TABLE `budget_profiles` (`id` TEXT NOT NULL PRIMARY KEY, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL);
CREATE TABLE `transactions` (`id` TEXT NOT NULL PRIMARY KEY, `budgetId` TEXT NOT NULL, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `category` TEXT NOT NULL, `subcategory` TEXT NOT NULL, `amount` REAL NOT NULL);
CREATE TABLE `goals` (`id` TEXT NOT NULL PRIMARY KEY, `budgetId` TEXT NOT NULL, `name` TEXT NOT NULL, `targetAmount` REAL NOT NULL, `currentAmount` REAL NOT NULL);
CREATE TABLE `categories` (`id` TEXT NOT NULL PRIMARY KEY, `budgetId` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL);
CREATE TABLE `ai_audits` (`id` TEXT NOT NULL PRIMARY KEY, `budgetId` TEXT NOT NULL, `periodType` TEXT NOT NULL, `periodKey` TEXT NOT NULL, `year` INTEGER NOT NULL, `month` INTEGER NOT NULL, `auditText` TEXT NOT NULL, `sillyExpensesSummary` TEXT NOT NULL, `timestamp` INTEGER NOT NULL);
