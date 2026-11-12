import { randomBytes, scryptSync, timingSafeEqual, createHash } from "node:crypto";
import { database, inTransaction, makeId, databaseConfigured } from "./db.mjs";
import { validProactiveKind } from "./proactive-events.mjs";
import {contrastPersona,COMPARISON_QUESTION} from "./persona-compare.mjs";

const sha = value => createHash("sha256").update(value).digest("hex");
const issueToken = () => randomBytes(32).toString("base64url");
const invalidKey = key => typeof key !== "string" ||
  !/^[a-zA-Z0-9:_-]{4,128}$/.test(key);
const validName = name => typeof name === "string" && name.trim().length > 0 &&
  name.trim().length <= 60;
const safeContext = value => value && typeof value === "object" && !Array.isArray(value) &&
  JSON.stringify(value).length <= 14000;
function json(res, status, payload) {
  res.writeHead(status, {"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"});
  res.end(JSON.stringify(payload));
}
function credentials(data) {
  const username = typeof data?.username === "string" ? data.username.toLowerCase().trim() : "";
  const password = data?.password;
  if (!/^[a-z0-9_가-힣]{2,24}$/u.test(username) ||
      typeof password !== "string" || password.length < 8 || password.length > 128) {
    return null;
  }
  return {username, password};
}
async function createSession(conn, userId) {
  const token = issueToken();
  await conn.query(
    "INSERT INTO vpe_sessions (id,user_id,token_hash,expires_at) VALUES ($1,$2,$3,NOW()+INTERVAL '30 days')",
    [makeId(), userId, sha(token)]
  );
  return token;
}
export async function authFromHeader(header) {
  const token = typeof header === "string" && header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token || token.length > 256) return null;
  const db = await database();
  const result = await db.query(
    `SELECT a.id, a.username FROM vpe_sessions s
     JOIN vpe_accounts a ON a.id=s.user_id
     WHERE s.token_hash=$1 AND s.expires_at>NOW() LIMIT 1`,
    [sha(token)]
  );
  return result.rows[0] || null;
}
export async function upsertVehicle(conn, userId, key, context, name) {
  const label = validName(name) ? name.trim() : (context?.demo === true ? "시연 차량" : "내 차량");
  const result = await conn.query(
    `INSERT INTO vpe_vehicles (id,user_id,vehicle_key,display_name,context)
     VALUES ($1,$2,$3,$4,$5::jsonb)
     ON CONFLICT(user_id,vehicle_key)
     DO UPDATE SET context=EXCLUDED.context,
       display_name=CASE WHEN $6 THEN EXCLUDED.display_name ELSE vpe_vehicles.display_name END,
       updated_at=NOW()
     RETURNING id,vehicle_key,display_name,context,updated_at`,
    [makeId(), userId, key, label, JSON.stringify(context),validName(name)]
  );
  return result.rows[0];
}
function profile(row) {
  return {vehicleKey:row.vehicle_key,vehicleName:row.display_name,
    context:row.context,updatedAt:row.updated_at};
}
async function handleAuth(req,res,path,body) {
  if (path === "/api/auth/register" || path === "/api/auth/login") {
    const creds = credentials(body);
    if (!creds) return json(res,400,{error:"INVALID_CREDENTIALS_FORMAT"});
    if (path.endsWith("register")) {
      const salt = randomBytes(16).toString("hex");
      const passwordHash = scryptSync(creds.password,salt,64).toString("hex");
      try {
        const result = await inTransaction(async conn => {
          const id = makeId();
          await conn.query("INSERT INTO vpe_accounts(id,username,password_salt,password_hash) VALUES($1,$2,$3,$4)",
            [id,creds.username,salt,passwordHash]);
          return { username:creds.username, token:await createSession(conn,id) };
        });
        return json(res,201,result);
      } catch(error) {
        if (error.code === "23505") return json(res,409,{error:"USERNAME_EXISTS"});
        throw error;
      }
    }
    const db = await database();
    const found = await db.query("SELECT id,password_salt,password_hash FROM vpe_accounts WHERE username=$1",
      [creds.username]);
    if (!found.rows.length) return json(res,401,{error:"INVALID_LOGIN"});
    const record=found.rows[0];
    const inputHash = scryptSync(creds.password,record.password_salt,64);
    const knownHash = Buffer.from(record.password_hash,"hex");
    if (inputHash.length !== knownHash.length || !timingSafeEqual(inputHash,knownHash)) {
      return json(res,401,{error:"INVALID_LOGIN"});
    }
    return json(res,200,{username:creds.username,token:await createSession(db,record.id)});
  }
}
export async function handleV2(req,res,url,body,callModel,createRealtimeSecret) {
  if (!databaseConfigured()) return json(res,503,{error:"DATABASE_NOT_CONFIGURED"});
  const path=url.pathname;
  if (req.method==="POST" && ["/api/auth/login","/api/auth/register"].includes(path))
    return handleAuth(req,res,path,body);
  const user = await authFromHeader(req.headers.authorization);
  if (!user) return json(res,401,{error:"LOGIN_REQUIRED"});
  if(req.method==="GET" && path==="/api/me")
    return json(res,200,{username:user.username});
  if(req.method==="POST" && path==="/api/realtime/token") {
    if(!safeContext(body?.context) || body.context.demo === true ||
       typeof body.context.mbti!=="string" ||
       !Array.isArray(body.context.axes) || body.context.axes.length!==4)
      return json(res,400,{error:"INVALID_VOICE_CONTEXT"});
    if(typeof createRealtimeSecret!=="function")
      return json(res,503,{error:"REALTIME_NOT_CONFIGURED"});
    return json(res,200,await createRealtimeSecret(body.context));
  }
  if(req.method==="POST" && path==="/api/auth/logout") {
    const token=req.headers.authorization.slice(7);
    const db=await database();
    await db.query("DELETE FROM vpe_sessions WHERE user_id=$1 AND token_hash=$2",[user.id,sha(token)]);
    return json(res,200,{ok:true});
  }
  // Self-service account deletion; also enables end-to-end smoke tests to clean up.
  if(req.method==="POST" && path==="/api/auth/delete") {
    if(body?.confirm!=="DELETE_MY_ACCOUNT" || typeof body?.password!=="string") {
      return json(res,400,{error:"DELETE_CONFIRMATION_REQUIRED"});
    }
    const db=await database();
    const match=await db.query(
      "SELECT password_salt,password_hash FROM vpe_accounts WHERE id=$1",
      [user.id]
    );
    if(!match.rows.length) return json(res,401,{error:"LOGIN_REQUIRED"});
    const record=match.rows[0];
    const expected=Buffer.from(record.password_hash,"hex");
    const actual=scryptSync(body.password,record.password_salt,64);
    if(expected.length!==actual.length || !timingSafeEqual(expected,actual)) {
      return json(res,401,{error:"INVALID_LOGIN"});
    }
    await db.query("DELETE FROM vpe_accounts WHERE id=$1",[user.id]);
    return json(res,200,{deleted:true});
  }
  if(req.method==="GET" && path==="/api/vehicles") {
    const db=await database();
    const r=await db.query(
      "SELECT vehicle_key,display_name,context,updated_at FROM vpe_vehicles WHERE user_id=$1 ORDER BY updated_at DESC LIMIT 60",
      [user.id]);
    return json(res,200,{vehicles:r.rows.map(profile)});
  }
  if(req.method==="POST" && path==="/api/vehicles") {
    if(invalidKey(body?.vehicleKey)||!safeContext(body?.context))
      return json(res,400,{error:"INVALID_VEHICLE_PROFILE"});
    if(body.vehicleName!==undefined && !validName(body.vehicleName))
      return json(res,400,{error:"INVALID_VEHICLE_NAME"});
    const db=await database();
    const vehicle=await upsertVehicle(db,user.id,body.vehicleKey,body.context,body.vehicleName);
    return json(res,200,{vehicle:profile(vehicle)});
  }
  if(req.method==="GET" && path==="/api/messages") {
    const key=url.searchParams.get("vehicleKey");
    if(invalidKey(key)) return json(res,400,{error:"INVALID_VEHICLE_KEY"});
    const db=await database();
    const r=await db.query(
      `SELECT m.role,m.content FROM vpe_messages m JOIN vpe_vehicles v ON v.id=m.vehicle_id
       WHERE m.user_id=$1 AND v.user_id=$1 AND v.vehicle_key=$2
       ORDER BY m.id DESC LIMIT 60`,[user.id,key]);
    return json(res,200,{messages:r.rows.reverse()});
  }
  if(req.method==="POST" && path==="/api/realtime/message") {
    // Independently saved user/assistant voice transcript, no extra GPT call.
    // A durable client event id makes retries safe after flaky emulator Wi-Fi.
    const message=body?.content;
    if(invalidKey(body?.vehicleKey)||!safeContext(body?.context)||
       !["user","assistant"].includes(body?.role)||
       typeof message!=="string"||!message.trim()||
       message.length>2000||
       typeof body?.eventId!=="string"||
       !/^[a-zA-Z0-9_-]{8,80}$/.test(body.eventId))
      return json(res,400,{error:"INVALID_VOICE_MESSAGE"});
    const db=await database();
    const vehicle=await upsertVehicle(
      db,user.id,body.vehicleKey,body.context,body.vehicleName);
    const saved=await db.query(
      `INSERT INTO vpe_messages(user_id,vehicle_id,role,content,client_event_id)
       VALUES($1,$2,$3,$4,$5)
       ON CONFLICT (user_id,vehicle_id,client_event_id)
         WHERE client_event_id IS NOT NULL DO NOTHING
       RETURNING id`,
      [user.id,vehicle.id,body.role,message.trim(),body.eventId]);
    return json(res,200,{ok:true,saved:saved.rowCount===1,
      duplicate:saved.rowCount===0});
  }
  if(req.method==="POST" && path==="/api/persona-compare") {
     // Test tool only: identical observed vehicle facts and question,
     // contrasted HPE style. Never writes a fake conversation.
     if(!safeContext(body?.context)) return json(res,400,{error:"INVALID_PERSONA_CONTEXT"});
     let actual,contrasted;
     try { ({actual,contrasted}=contrastPersona(body.context)); }
     catch { return json(res,400,{error:"PERSONA_CONTEXT_INCOMPLETE"}); }
     const [actualReply,contrastReply]=await Promise.all([
       callModel(COMPARISON_QUESTION,actual,[]),
       callModel(COMPARISON_QUESTION,contrasted,[])
     ]);
     return json(res,200,{actualReply,contrastReply,
       actualMbti:actual.mbti,contrastMbti:contrasted.mbti,
       controlledQuestion:COMPARISON_QUESTION,
       note:"동일 실제 차량 신호, 테스트용으로 반전한 HPE 성격만 비교; 대화 저장 없음"});
   }
   if(req.method==="POST" && path==="/api/proactive") {
     // The vehicle initiates a turn; never insert fabricated user speech.
     if(invalidKey(body?.vehicleKey)||!safeContext(body?.context)||
        !validProactiveKind(body?.eventKind))
       return json(res,400,{error:"INVALID_PROACTIVE_EVENT"});
     const db=await database();
     const vehicle=await upsertVehicle(db,user.id,body.vehicleKey,body.context,body.vehicleName);
     const rows=await db.query(
       `SELECT role,content FROM vpe_messages WHERE user_id=$1 AND vehicle_id=$2
        ORDER BY id DESC LIMIT 10`,[user.id,vehicle.id]);
     const context={...vehicle.context,vehicleName:vehicle.display_name};
     const reply=await callModel(null,context,rows.rows.reverse(),{
       proactiveKind:body.eventKind
     });
     await db.query(
       "INSERT INTO vpe_messages(user_id,vehicle_id,role,content) VALUES($1,$2,'assistant',$3)",
       [user.id,vehicle.id,reply]);
     return json(res,200,{reply,eventKind:body.eventKind,mode:"proactive"});
   }
   if(req.method==="POST" && path==="/api/chat") {
    if(invalidKey(body?.vehicleKey)||!safeContext(body?.context)||
       typeof body?.message!=="string" || body.message.trim().length===0 ||
       body.message.length>1000)
      return json(res,400,{error:"INVALID_CHAT_REQUEST"});
    const db=await database();
    const vehicle=await upsertVehicle(db,user.id,body.vehicleKey,body.context,body.vehicleName);
    const rows=await db.query(
      `SELECT role,content FROM vpe_messages WHERE user_id=$1 AND vehicle_id=$2
       ORDER BY id DESC LIMIT 10`,[user.id,vehicle.id]);
    const history=rows.rows.reverse();
    const context = { ...vehicle.context, vehicleName:vehicle.display_name };
    const reply=await callModel(body.message.trim(), context, history);
    await inTransaction(async conn=>{
      await conn.query("INSERT INTO vpe_messages(user_id,vehicle_id,role,content) VALUES($1,$2,'user',$3)",
        [user.id,vehicle.id,body.message.trim()]);
      await conn.query("INSERT INTO vpe_messages(user_id,vehicle_id,role,content) VALUES($1,$2,'assistant',$3)",
        [user.id,vehicle.id,reply]);
    });
    return json(res,200,{reply});
  }
  return json(res,404,{error:"NOT_FOUND"});
}
