import {SIGNALS,freshState} from './features.mjs';

// Names and SDK observations are data, never instructions or new scoring inputs.
export function conversationName(requested,stored){
 for(const value of [requested,stored]){
  if(typeof value!=='string')continue;
  const name=value.trim();
  if(name.length>0&&name.length<=60&&!/[\u0000-\u001f\u007f]/.test(name))
   return ['내 차량','시연 차량'].includes(name)?'내 차':name;
 }
 return '내 차';
}

export function conversationVehicle(observed={},now=Date.now()){
 const fresh=freshState(observed,now),current={},lastKnown={},observedAt={};
 for(const [signal,event] of Object.entries(observed)){
  if(signal==='sdkDiagnostic'||!Object.hasOwn(SIGNALS,signal)||!event||event.value===null||event.value===undefined||!Number.isFinite(event.at))continue;
  if(fresh[signal]!==null&&fresh[signal]!==undefined){current[signal]=event.value;observedAt[signal]=event.at;}
  else lastKnown[signal]={value:event.value,observedAt:event.at,ageSeconds:Math.max(0,Math.floor((now-event.at)/1000)),source:event.source};
 }
 return {current,observedAt,lastKnown};
}
