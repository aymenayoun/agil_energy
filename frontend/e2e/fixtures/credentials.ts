/**
 * Test credentials for E2E suite.
 * These match a seeded admin in init-db/init.sql.
 */
export const ADMIN = {
  email: 'admin@agil.tn',
  password: process.env.E2E_ADMIN_PASSWORD ?? '',
};
