import { randomUUID } from "node:crypto";

let pool = null;
let booting = null;

export function databaseConfigured() {
  return Boolean(process.env.DATABASE_URL);
}

export async function database() {
  if (!databaseConfigured()) throw new Error("DATABASE_NOT_CONFIGURED");
  if (pool) return pool;
  if (!booting) {
    booting = (async () => {
      // Loaded lazily so the pre-database VPE service remains online.
      const { Pool } = await import("pg");
      const connectionString = process.env.DATABASE_URL;
      const candidate = new Pool({ connectionString, max: 5, connectionTimeoutMillis: 8000,
        idleTimeoutMillis: 30000 });
      try {
        await candidate.query("SELECT 1");
        await candidate.query(`
          CREATE TABLE IF NOT EXISTS vpe_accounts (
            id UUID PRIMARY KEY,
            username VARCHAR(24) NOT NULL UNIQUE,
            password_salt TEXT NOT NULL,
            password_hash TEXT NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
          );
          CREATE TABLE IF NOT EXISTS vpe_sessions (
            id UUID PRIMARY KEY,
            user_id UUID NOT NULL REFERENCES vpe_accounts(id) ON DELETE CASCADE,
            token_hash CHAR(64) NOT NULL UNIQUE,
            expires_at TIMESTAMPTZ NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
          );
          CREATE INDEX IF NOT EXISTS vpe_sessions_owner ON vpe_sessions(user_id);
          CREATE TABLE IF NOT EXISTS vpe_vehicles (
            id UUID PRIMARY KEY,
            user_id UUID NOT NULL REFERENCES vpe_accounts(id) ON DELETE CASCADE,
            vehicle_key VARCHAR(128) NOT NULL,
            display_name VARCHAR(60) NOT NULL,
            context JSONB NOT NULL DEFAULT '{}'::jsonb,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            UNIQUE (user_id, vehicle_key)
          );
          CREATE INDEX IF NOT EXISTS vpe_vehicles_owner ON vpe_vehicles(user_id);
          CREATE TABLE IF NOT EXISTS vpe_messages (
            id BIGSERIAL PRIMARY KEY,
            user_id UUID NOT NULL REFERENCES vpe_accounts(id) ON DELETE CASCADE,
            vehicle_id UUID NOT NULL REFERENCES vpe_vehicles(id) ON DELETE CASCADE,
            role VARCHAR(10) NOT NULL CHECK (role IN ('user', 'assistant')),
            content TEXT NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
          );
          CREATE INDEX IF NOT EXISTS vpe_messages_scope
            ON vpe_messages(user_id, vehicle_id, id DESC);
        `);
        pool = candidate;
        return candidate;
      } catch (error) {
        await candidate.end().catch(() => {});
        throw error;
      }
    })().finally(() => { booting = null; });
  }
  return booting;
}

export async function inTransaction(work) {
  const db = await database();
  const conn = await db.connect();
  try {
    await conn.query("BEGIN");
    const result = await work(conn);
    await conn.query("COMMIT");
    return result;
  } catch (error) {
    await conn.query("ROLLBACK").catch(() => {});
    throw error;
  } finally {
    conn.release();
  }
}

export function makeId() { return randomUUID(); }
