import {auditCharacters} from './audit.mjs';
import {randomUUID} from 'node:crypto';
import {authFromHeader,upsertVehicle} from '../account-api.mjs';
import {database} from '../db.mjs';
import {schema,mutate,observations,applyMeaning,recordResult} from './store.mjs';
import {normalizeEvents,freshState,cellOf} from './features.mjs';
import {makePlan,hash} from './engine.mjs';
import {packetFor,compile,report,voiceProfile} from './context.mjs';
import {extractMeaning} from './semantic.mjs';
const respond=(res,status,data)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(data));};
const repliesInFlight=new Map();
async function oneReply(key,fn){if(repliesInFlight.has(key))return repliesInFlight.get(key);const task=Promise.resolve().then(fn);repliesInFlight.set(key,task);try{return await task;}finally{repliesInFlight.delete(key);}}
const validId=x=>typeof x==='string'&&/^[A-Za-z0-9:_-]{4,100}$/.test(x);
function memoriesFor(profile,text,now){const tokens=new Set(text.toLowerCase().match(/[\p{L}\p{N}]+/gu)||[]);return profile.memories.map(m=>{const words=(m.text+' '+m.quote).toLowerCase().match(/[\p{L}\p{N}]+/gu)||[];const relevance=words.length?words.filter(w=>tokens.has(w)).length/Math.max(1,Math.min(tokens.size,words.length)):0;return {...m,score:.5*Math.min(1,relevance)+.25*m.importance+.15*Math.exp(-(now-m.at)/86400000/30)+.1};}).filter(m=>m.score>=.55).sort((a,b)=>b.score-a.score).slice(0,4);}
export async function handleCharacter(req,res,url,body,callModel,createSecret){
 const user=await authFromHeader(req.headers.authorization);if(!user)return respond(res,401,{error:'LOGIN_REQUIRED'});
 if(url.pathname==='/api/v3/persona-audit'&&req.method==='POST'){const result=await auditCharacters(user.id,body,callModel);return respond(res,result.limited?429:result.invalid?400:200,result);}
 const key=body?.vehicleKey||url.searchParams.get('vehicleKey');if(typeof key!=='string'||!/^[A-Za-z0-9:_-]{4,128}$/.test(key))return respond(res,400,{error:'INVALID_VEHICLE_KEY'});
 await schema(); const db=await database();const existing=await db.query('SELECT * FROM vpe_vehicles WHERE user_id=$1 AND vehicle_key=$2',[user.id,key]);const vehicle=existing.rows[0]||await upsertVehicle(db,user.id,key,{},body?.vehicleName);
 const path=url.pathname,now=Date.now();
 if(path.endsWith('/observations')&&req.method==='POST'){
  let events;try{events=normalizeEvents(body.events,now);}catch{return respond(res,400,{error:'INVALID_OBSERVATIONS'});}
  const result=await observations(user.id,vehicle.id,events,now);return respond(res,200,{accepted:result.accepted,duplicate:result.duplicate,character:report(result.profile),features:result.state.features});
 }
 if(path.endsWith('/persona')&&req.method==='GET'){const result=await mutate(user.id,vehicle.id,s=>({character:report(s.profile),features:s.features||null}));return respond(res,200,result);}
 if(path.endsWith('/turn/result')&&req.method==='POST'){
  if(!validId(body.turnId)||typeof body.heard!=='boolean')return respond(res,400,{error:'INVALID_TURN_RESULT'});
  return respond(res,200,await recordResult(user.id,vehicle.id,body.turnId,body.heard));
 }
 const token=path.endsWith('/realtime/token'),chat=path.endsWith('/chat'),planning=path.endsWith('/turn/plan');
 if(req.method!=='POST'||!token&&!chat&&!planning)return respond(res,404,{error:'NOT_FOUND'});
 const text=body.message||'';if(!token&&(typeof text!=='string'||!text.trim()||text.length>4000))return respond(res,400,{error:'INVALID_MESSAGE'});
 const turnId=body.turnId||randomUUID();if(!validId(turnId))return respond(res,400,{error:'INVALID_TURN_ID'});
 const rows=await db.query('SELECT role,content FROM vpe_messages WHERE user_id=$1 AND vehicle_id=$2 ORDER BY id DESC LIMIT 60',[user.id,vehicle.id]);const history=rows.rows.reverse();
 // Replay a finalized plan, not its side effects. Auth is always checked first.
 const cached=await db.query('SELECT packet FROM vpe_character_turns WHERE vehicle_id=$1 AND turn_id=$2',[vehicle.id,turnId]);
 let packet,compiled,profile,meaning={};
 if(cached.rowCount){packet=cached.rows[0].packet;compiled=compile(packet);profile=await mutate(user.id,vehicle.id,s=>s.profile);}
 else{
  meaning=token?{}:await extractMeaning(text,history);
  const result=await mutate(user.id,vehicle.id,async(s,conn)=>{
   const priorPlan=await conn.query('SELECT packet FROM vpe_character_turns WHERE vehicle_id=$1 AND turn_id=$2',[vehicle.id,turnId]);
   if(priorPlan.rowCount)return {packet:priorPlan.rows[0].packet,profile:s.profile};
   applyMeaning(s.profile,meaning,text,turnId,now);
   const state=freshState(s.observed,now);const memories=memoriesFor(s.profile,text,now);
   const context={state,userTurn:true,cell:s.active?cellOf(s.active):null,memories,proactiveAllowed:false,preferences:Object.keys(s.profile.preferences)};
   if(s.profile.preferences.quietUntil>now)meaning.quiet=true;
   const plan=makePlan(s.profile,context,meaning,now);s.profile.affect=plan.affect;
   const p=packetFor(s.profile,plan,context,turnId);p.fallback=meaning.fallback||null;p.history=history.slice(-12).map(m=>({...m,content:m.content.slice(0,1500)}));p.latestUserText=text;
   await conn.query('INSERT INTO vpe_character_turns(vehicle_id,turn_id,packet) VALUES($1,$2,$3::jsonb) ON CONFLICT DO NOTHING',[vehicle.id,turnId,JSON.stringify(p)]);
   return {packet:p,profile:s.profile};
  });packet=result.packet;profile=result.profile;compiled=compile(packet);
 }
 const output={packet,instructions:compiled.instructions,coreHash:compiled.coreHash,turnId,epoch:body.epoch,character:report(profile)};
 if(token){const voice=voiceProfile(profile,body.voicePresentation);return respond(res,200,{...await createSecret({characterInstructions:compiled.instructions,characterVoice:voice},history),...output});}
 if(planning)return respond(res,200,output);
 const reply=await oneReply(user.id+':'+vehicle.id+':'+turnId,async()=>{
  const prior=await db.query("SELECT content FROM vpe_messages WHERE user_id=$1 AND vehicle_id=$2 AND client_event_id=$3",[user.id,vehicle.id,turnId+'-a']);
  if(prior.rowCount)return prior.rows[0].content;
  const generated=await callModel(text,{characterInstructions:compiled.instructions},history);
  await db.query("INSERT INTO vpe_messages(user_id,vehicle_id,role,content,client_event_id) VALUES($1,$2,'user',$3,$4),($1,$2,'assistant',$5,$6) ON CONFLICT (user_id,vehicle_id,client_event_id) WHERE client_event_id IS NOT NULL DO NOTHING",[user.id,vehicle.id,text,turnId+'-u',generated,turnId+'-a']);
  await recordResult(user.id,vehicle.id,turnId,true);return generated;
 });return respond(res,200,{reply,...output});
}
