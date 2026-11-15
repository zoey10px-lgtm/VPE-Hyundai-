import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID,createHash} from 'node:crypto';
import {database} from '../db.mjs';
import {handleCharacter} from './api.mjs';
test('Postgres: ownership, event deduplication, persisted plans, chat replay and cascade', {skip:!process.env.TEST_DATABASE},async()=>{
 const db=await database(),uid=randomUUID(),token=randomUUID();
 await db.query('INSERT INTO vpe_accounts(id,username,password_salt,password_hash) VALUES($1,$2,$3,$4)',[uid,'test_'+uid.slice(0,8),'test','test']);
 await db.query("INSERT INTO vpe_sessions(id,user_id,token_hash,expires_at) VALUES($1,$2,$3,NOW()+INTERVAL '1 hour')",[randomUUID(),uid,createHash('sha256').update(token).digest('hex')]);
 let calls=0;
 async function request(path,body,auth=token,method='POST'){
  let status,payload;const res={writeHead:s=>status=s,end:x=>payload=JSON.parse(x)};
  await handleCharacter({method,headers:{authorization:'Bearer '+auth}},res,new URL('http://test/api/v3/'+path),body,async()=>{calls++;return '함께 가자. 오늘은 어떤 기분이야?';},async()=>({token:'ek_test'}));
  return {status,...payload};
 }
 try{
  assert.equal((await request('observations',{vehicleKey:'test-car',events:[]},'invalid')).status,401);
  const event={id:randomUUID(),signal:'energyPercent',value:12,at:Date.now(),source:'real'};
  const first=await request('observations',{vehicleKey:'test-car',events:[event]});assert.equal(first.accepted,1);
  assert.equal((await request('observations',{vehicleKey:'test-car',events:[event]})).duplicate,1);
  const at=Date.now();
  const pending=Object.entries({odometerKm:0,passenger:true,cruiseControlState:'ENABLED',hvacOn:true,charging:false,batteryCapacityRaw:'77400.0'}).map(([signal,value])=>({id:randomUUID(),signal,value,at,source:'emulator'}));
  const body={vehicleKey:'test-car',message:'안녕',turnId:randomUUID(),observations:pending};
  const concurrent=await Promise.all([request('chat',body),request('chat',body)]);
  const chat=concurrent[0];assert.equal(concurrent[1].reply,chat.reply);assert.equal(chat.status,200);assert.ok(chat.instructions.includes('energyPercent'));
  assert.equal(chat.character.formation,'sdk_initial');assert.equal(chat.character.algorithm,'character.v3.0.1');
  assert.ok(chat.character.scoring.ready);assert.equal(chat.character.scoring.groups.length>=4,true);
  assert.ok(chat.packet.facts.some(x=>x.signal==='batteryCapacityRaw'&&x.value==='77400.0'));
  assert.deepEqual(chat.packet.initialEvidence,chat.character.initialEvidence);
  for(const [k,v] of Object.entries(chat.character.traits)) {
   // Conversation closeness may raise these three expression values; all other
   // dimensions must exactly match the character shown in the report.
   if(!['disclosure','expressiveness','social_appetite'].includes(k))assert.equal(chat.packet.traits[k],v,k);
  }
  const persona=await request('persona?vehicleKey=test-car',{},token,'GET');
  assert.deepEqual(persona.character.traits,chat.character.traits);
  assert.equal((await request('chat',body)).reply,chat.reply);assert.equal(calls,1);
  const profiles=await db.query('SELECT data FROM vpe_characters c JOIN vpe_vehicles v ON c.vehicle_id=v.id WHERE v.user_id=$1',[uid]);
  assert.equal(profiles.rows[0].data.profile.actions.length,1);
  const eventCount=await db.query('SELECT COUNT(*)::int n FROM vpe_character_events e JOIN vpe_vehicles v ON e.vehicle_id=v.id WHERE v.user_id=$1',[uid]);
  assert.equal(eventCount.rows[0].n,1+pending.length);
 }finally{await db.query('DELETE FROM vpe_accounts WHERE id=$1',[uid]);}
});

