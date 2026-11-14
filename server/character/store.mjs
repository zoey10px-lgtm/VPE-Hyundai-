import {database,inTransaction} from '../db.mjs';
import {initialProfile,updateTraits,values} from './engine.mjs';
import {ingest,aggregate} from './features.mjs';
let boot;
export async function schema(){
 if(!boot)boot=(async()=>{const db=await database();await db.query(`
 CREATE TABLE IF NOT EXISTS vpe_characters(vehicle_id UUID PRIMARY KEY REFERENCES vpe_vehicles(id) ON DELETE CASCADE, data JSONB NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
 CREATE TABLE IF NOT EXISTS vpe_character_events(vehicle_id UUID NOT NULL REFERENCES vpe_vehicles(id) ON DELETE CASCADE,event_id VARCHAR(100) NOT NULL,at TIMESTAMPTZ NOT NULL,PRIMARY KEY(vehicle_id,event_id));
 CREATE TABLE IF NOT EXISTS vpe_character_turns(vehicle_id UUID NOT NULL REFERENCES vpe_vehicles(id) ON DELETE CASCADE,turn_id VARCHAR(100) NOT NULL, packet JSONB NOT NULL, heard BOOLEAN NOT NULL DEFAULT false, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),PRIMARY KEY(vehicle_id,turn_id));
 `);})().catch(e=>{boot=null;throw e;});return boot;
}
export async function mutate(userId,vehicleId,fn){
 await schema();return inTransaction(async db=>{
  const owned=await db.query('SELECT id FROM vpe_vehicles WHERE id=$1 AND user_id=$2',[vehicleId,userId]);if(!owned.rowCount)throw new Error('VEHICLE_NOT_FOUND');
  const fresh={profile:initialProfile(vehicleId),source:'real',observed:{},trips:[]};
  await db.query('INSERT INTO vpe_characters(vehicle_id,data) VALUES($1,$2::jsonb) ON CONFLICT DO NOTHING',[vehicleId,JSON.stringify(fresh)]);
  const r=await db.query('SELECT data FROM vpe_characters WHERE vehicle_id=$1 FOR UPDATE',[vehicleId]);const state=r.rows[0].data;
  const result=await fn(state,db);await db.query('UPDATE vpe_characters SET data=$2::jsonb,updated_at=NOW() WHERE vehicle_id=$1',[vehicleId,JSON.stringify(state)]);return result;
 });
}
export async function observations(userId,vehicleId,events,now=Date.now()){
 return mutate(userId,vehicleId,async(state,db)=>{
  const unique=[];for(const e of events){const r=await db.query('INSERT INTO vpe_character_events(vehicle_id,event_id,at) VALUES($1,$2,$3) ON CONFLICT DO NOTHING RETURNING event_id',[vehicleId,e.id,new Date(e.at)]);if(r.rowCount)unique.push(e);}
  const changed=ingest(state,unique,now);Object.assign(state,changed.state);
  const snapshot=aggregate(state.trips,state.profile.feedback,now,state.refills||[]);state.profile=updateTraits(state.profile,snapshot,now);state.profile.values=values(state.profile.traits);state.features=snapshot;
  await db.query("DELETE FROM vpe_character_events WHERE vehicle_id=$1 AND at<NOW()-INTERVAL '8 days'",[vehicleId]);
  return {profile:state.profile,state,accepted:unique.length,duplicate:events.length-unique.length};
 });
}
export function applyMeaning(profile,semantic,text,turnId,now=Date.now()){
 if(semantic.preference){const p=semantic.preference;const recent=profile.feedback.find(x=>x.feature===p.feature&&now-x.at<600000);
  if(!recent&&!profile.feedback.some(x=>x.id===turnId)){profile.feedback.push({...p,id:turnId,at:now});profile.feedback=profile.feedback.slice(-2000);}
  if(p.feature==='D10')profile.preferences.teasing=p.value>=.5;
  if(p.feature==='D01')profile.preferences.proactive=p.value>=.5;
 }
 if(semantic.quiet)profile.preferences.quietUntil=now+2*3600000;
 if(semantic.memory&&!profile.memories.some(x=>x.sourceId===turnId))profile.memories.push({id:'memory-'+turnId,sourceId:turnId,quote:semantic.memory.quote,text:semantic.memory.text,importance:semantic.memory.importance,at:now,kind:'user_fact'});
 profile.memories=profile.memories.slice(-200);
 const day=new Date(now+9*3600000).toISOString().slice(0,10);
 if(!profile.relationshipDays.includes(day)&&!semantic.quiet)profile.relationshipDays.push(day);
 profile.relationshipDays=profile.relationshipDays.slice(-90);
}
export async function recordResult(userId,vehicleId,turnId,heard){
 if(!heard)return {ok:true};
 return mutate(userId,vehicleId,async(state,db)=>{const r=await db.query('UPDATE vpe_character_turns SET heard=true WHERE vehicle_id=$1 AND turn_id=$2 AND heard=false RETURNING packet',[vehicleId,turnId]);
  if(heard&&r.rowCount){state.profile.actions.push(r.rows[0].packet.action.primary);state.profile.actions=state.profile.actions.slice(-30);}return {ok:true};});
}
