// Isolated production check. Credentials remain in memory and account is deleted.
import {randomBytes,randomUUID} from 'node:crypto';
const endpoint=process.env.VPE_LIVE_URL||'https://vpe-hyundai.onrender.com';
const username='vpename_'+randomBytes(5).toString('hex'),password=randomBytes(24).toString('base64url');
let token;
async function request(path,body){
 const r=await fetch(endpoint+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(50000)});
 const result=await r.json();if(!r.ok)throw Error(path+': '+r.status+' '+(result.error||''));return result;
}
const assert=(condition,message)=>{if(!condition)throw Error(message);};
try{
 const health=await request('/health');assert(health.conversationVersion==='vehicle-dialogue.v1.1','New dialogue server is not live');
 const account=await request('/api/auth/register',{username,password});token=account.token;
 async function chat(message,values){
  const at=Date.now();const observations=Object.entries(values).map(([signal,value])=>({id:randomUUID(),signal,value,at,source:'emulator'}));
  const r=await request('/api/v3/chat',{vehicleKey:'dialogue-check-car',vehicleName:'삣삐',message,turnId:randomUUID(),observations});
  assert(r.packet.identity.name==='삣삐','Registered name lost');
  console.log(JSON.stringify({message,reply:r.reply,conversation:r.packet.conversationVersion}));return r;
 }
 const cold={parked:true,passenger:false,odometerKm:12345,actualCabinC:3,targetCabinC:22,energyPercent:80,batteryCapacityRaw:'77400.0',cruiseControlState:'ENABLED'};
 const name=await chat('삣삐야! 네 이름이 뭐야?',cold);
 assert(name.reply.includes('삣삐'),'Reply failed to recognize own name');
 assert(!/(욕설|비속어|욕하지|예쁜 말)/.test(name.reply),'Name misread as profanity');
 const chill=await chat('너 지금 실내 어때? 네가 느끼는 걸 말해 줘.',cold);
 assert(/춥|추워|차가|시려|시리|오들|얼어|서늘|덜덜/.test(chill.reply),'Cold observation missing from response');
 assert(chill.packet.vehicle.current.batteryCapacityRaw==='77400.0','Full telemetry lost');
 const hot=await chat('지금은 네 실내가 어때? 네 상태를 말해 줘.',{...cold,actualCabinC:35});
 assert(/덥|더워|뜨거|후끈|찜통|열기|답답/.test(hot.reply),'Hot update missing from response');
 console.log('PASS: registered nickname, cold-to-hot dialogue and full received vehicle context');
}finally{
 if(token){const deleted=await request('/api/auth/delete',{password,confirm:'DELETE_MY_ACCOUNT'});assert(deleted.deleted===true,'Temporary account cleanup failed');console.log('PASS: temporary account and test vehicle deleted');}
}
