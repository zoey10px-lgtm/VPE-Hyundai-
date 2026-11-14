import {usageFixture,USAGE_CASES} from './fixtures/usage.mjs';
import {makePlan,hash} from './engine.mjs';
import {packetFor,compile,report} from './context.mjs';
import {localMeaning} from './semantic.mjs';
let fixtures;
const recent=new Map();
export async function auditCharacters(userId,body,callModel){
 const now=Date.now(),old=recent.get(userId)||0;
 if(now-old<10000)return {limited:true};
 const messages=body.messages||[body.message||'오늘 처음으로 바다 보러 가! 너도 신나?'];
 if(!Array.isArray(messages)||messages.length<1||messages.length>4||messages.some(x=>typeof x!=='string'||!x.trim()||x.length>600))return {invalid:true};
 recent.set(userId,now);for(const[k,v]of recent)if(now-v>600000)recent.delete(k);
 fixtures??=Object.keys(USAGE_CASES).map(name=>usageFixture(name));
 const cases=await Promise.all(fixtures.map(async fixture=>{
  const profile=structuredClone(fixture.profile),history=[],turns=[];
  // Identical current sensors, relationship and user turns. Only usage history differs.
  profile.relationshipDays=Array.from({length:10},(_,i)=>'fixture-day-'+i);
  for(let i=0;i<messages.length;i++){
   const message=messages[i],context={state:{parked:true,energyPercent:80},userTurn:true,memories:[]};
   const plan=makePlan(profile,context,localMeaning(message),now+i*30000);
   const packet=packetFor(profile,plan,context,'audit-'+i);packet.history=history.slice(-6);
   const {instructions,coreHash}=compile(packet);
   const reply=await callModel(message,{characterInstructions:instructions},history);
   turns.push({message,reply,action:packet.action,coreHash});history.push({role:'user',content:message},{role:'assistant',content:reply});profile.actions.push(plan.primary);profile.affect=plan.affect;
  }
  return {usage:fixture.name,synthetic:true,tripCount:fixture.tripCount,eventCount:fixture.eventCount,evidenceHash:hash(fixture.snapshot),character:report(profile),turns};
 }));
 return {synthetic:true,protocol:'same vehicle ID, same present state, same messages; different synthetic SDK histories',messages,cases};
}
