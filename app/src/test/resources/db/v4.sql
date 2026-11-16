-- Frozen fixture derived from Entities.kt at 966489c (Room v4).
CREATE TABLE `budget_profiles` (`id` TEXT NOT NULL PRIMARY KEY, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL);
CREATE TABLE `accounts` (`id` TEXT NOT NULL PRIMARY KEY, `budgetId` TEXT NOT NULL, `name` TEXT NOT NULL, `balance` REAL NOT NULL, `type` TEXT NOT NULL, `accountNumber` TEXT NOT NULL);
CREATE TABLE `notifications` (`id` TEXT NOT NULL PRIMARY KEY, `budgetId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isRead` INTEGER NOT NULL);
CREATE TABLE `transactions` (`id` TEXT NOT NULL PRIMARY KEY, `budgetId` TEXT NOT NULL, `accountId` TEXT, `type` TEXT NOT NULL, `date` TEXT NOT NULL, `category` TEXT NOT NULL, `subcategory` TEXT NOT NULL, `amount` REAL NOT NULL);
CREATE TABLE `goals` (`id` TEXT NOT NULL PRIMARY KEY, `budgetId` TEXT NOT NULL, `name` TEXT NOT NULL, `targetAmount` REAL NOT NULL, `currentAmount` REAL NOT NULL);
CREATE TABLE `categories` (`id` TEXT NOT NULL PRIMARY KEY, `budgetId` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `monthlyLimit` REAL);
CREATE TABLE `ai_audits` (`id` TEXT NOT NULL PRIMARY KEY, `budgetId` TEXT NOT NULL, `periodType` TEXT NOT NULL, `periodKey` TEXT NOT NULL, `year` INTEGER NOT NULL, `month` INTEGER NOT NULL, `auditText` TEXT NOT NULL, `sillyExpensesSummary` TEXT NOT NULL, `timestamp` INTEGER NOT NULL);
