-- Runs once, on the first start of the MySQL container.
-- The 'budgetx' account itself is created by the mysql image from MYSQL_USER / MYSQL_PASSWORD
-- (see docker-compose.yml), so no password is stored in this file.

CREATE DATABASE IF NOT EXISTS budgetx_users;
CREATE DATABASE IF NOT EXISTS budgetx_emi;
CREATE DATABASE IF NOT EXISTS budgetx_expense;
CREATE DATABASE IF NOT EXISTS budgetx_budget;
CREATE DATABASE IF NOT EXISTS budgetx_ai;
CREATE DATABASE IF NOT EXISTS budgetx_market;
CREATE DATABASE IF NOT EXISTS budgetx_investment;
CREATE DATABASE IF NOT EXISTS budgetx_notification;

GRANT ALL PRIVILEGES ON budgetx_users.*        TO 'budgetx'@'%';
GRANT ALL PRIVILEGES ON budgetx_emi.*          TO 'budgetx'@'%';
GRANT ALL PRIVILEGES ON budgetx_expense.*      TO 'budgetx'@'%';
GRANT ALL PRIVILEGES ON budgetx_budget.*       TO 'budgetx'@'%';
GRANT ALL PRIVILEGES ON budgetx_ai.*           TO 'budgetx'@'%';
GRANT ALL PRIVILEGES ON budgetx_market.*       TO 'budgetx'@'%';
GRANT ALL PRIVILEGES ON budgetx_investment.*   TO 'budgetx'@'%';
GRANT ALL PRIVILEGES ON budgetx_notification.* TO 'budgetx'@'%';
FLUSH PRIVILEGES;
