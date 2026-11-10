import { randomUUID, randomBytes, scryptSync, createHash } from "node:crypto";
import { database, databaseConfigured } from "./db.mjs";

/**
 * Startup DB connectivity and multi-tenant isolation check.
 * All probe rows are written in one transaction that is ALWAYS rolled back.
 * No user credentials or vehicle information are logged.
 */
export async function verifyDatabaseStartup() {
  if (!databaseConfigured()) {
    console.warn("VPE_DB_PREFLIGHT_SKIPPED missing DATABASE_URL");
    return false;
  }
  let conn;
  try {
    const pool = await database(); // Creates the app tables when needed.
    conn = await pool.connect();
    await conn.query("BEGIN");

    const a = randomUUID();
    const b = randomUUID();
    const suffix = randomBytes(6).toString("hex");
    const passwordSalt = randomBytes(16).toString("hex");
    const passwordHash = scryptSync("startup-only-not-a-real-password", passwordSalt, 64).toString("hex");
    await conn.query(
      `INSERT INTO vpe_accounts(id,username,password_salt,password_hash)
       VALUES ($1,$2,$3,$4),($5,$6,$3,$4)`,
      [a, "probea_"+suffix,passwordSalt,passwordHash,b,"probeb_"+suffix]
    );
    const key = "probe:shared-key";
    const va=randomUUID();
    const vb=randomUUID();
    await conn.query(
      `INSERT INTO vpe_vehicles(id,user_id,vehicle_key,display_name,context)
       VALUES ($1,$2,$5,'car a','{}'::jsonb),($3,$4,$5,'car b','{}'::jsonb)`,
      [va,a,vb,b,key]
    );
    await conn.query(
      `INSERT INTO vpe_messages(user_id,vehicle_id,role,content)
       VALUES ($1,$2,'user','hello-a'),($3,$4,'user','hello-b')`,
      [a,va,b,vb]
    );
    const ownA=await conn.query(
      `SELECT m.content FROM vpe_messages m JOIN vpe_vehicles v ON v.id=m.vehicle_id
       WHERE m.user_id=$1 AND v.user_id=$1 AND v.vehicle_key=$2`,
      [a,key]
    );
    const ownB=await conn.query(
      `SELECT m.content FROM vpe_messages m JOIN vpe_vehicles v ON v.id=m.vehicle_id
       WHERE m.user_id=$1 AND v.user_id=$1 AND v.vehicle_key=$2`,
      [b,key]
    );
    if(ownA.rowCount!==1 || ownA.rows[0].content!=="hello-a" ||
       ownB.rowCount!==1 || ownB.rows[0].content!=="hello-b") {
      throw new Error("ACCOUNT_VEHICLE_ISOLATION_FAILED");
    }
    const tables=await conn.query(
      `SELECT count(*)::int AS total FROM pg_tables
       WHERE schemaname='public' AND tablename = ANY($1::text[])`,
      [["vpe_accounts","vpe_sessions","vpe_vehicles","vpe_messages"]]
    );
    if(tables.rows[0].total!==4) throw new Error("DATABASE_SCHEMA_INCOMPLETE");
    await conn.query("ROLLBACK");
    console.log("VPE_DB_PREFLIGHT_PASS schema=4, account_scope=pass, chat_scope=pass, rollback=pass");
    return true;
  } catch(err) {
    console.error("VPE_DB_PREFLIGHT_FAILED:", err.code || err.message || "UNKNOWN");
    if(conn) await conn.query("ROLLBACK").catch(()=>{});
    return false;
  } finally {
    conn?.release();
  }
}
