import {createHash} from 'node:crypto';
export const VERSION='character.v1.0';
export const NAMES=['initiative','expressiveness','disclosure','social_appetite','curiosity','abstraction','analytical','empathy','planning','flexibility','playfulness','directness','warmth','autonomy','reactivity','recovery'];
export const W={initiative:{D01:.7},expressiveness:{D02:.8},disclosure:{D03:.6,D04:.2},social_appetite:{D01:.55,D04:.25},curiosity:{F01:.8,F02:.5,F04:.25,F12:.2},abstraction:{D05:.8},analytical:{D06:.8},empathy:{D07:.8},planning:{F03:.65,F05:.25,F06:.2,D08:.3},flexibility:{F04:.35,F10:.2,D09:.35},playfulness:{D10:.8},directness:{D11:.8},warmth:{D12:.8},autonomy:{D13:.8},reactivity:{D14:.8},recovery:{D15:-.8}};
export const clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
export const finite=x=>typeof x==='number'&&Number.isFinite(x);
const sig=x=>1/(1+Math.exp(-x));
export const hash=x=>createHash('sha256').update(typeof x==='string'?x:JSON.stringify(x)).digest('hex');
export function initialProfile(key){
 const base=Object.fromEntries(NAMES.map(k=>[k,.25+.5*parseInt(hash(key+'|'+k).slice(0,8),16)/0xffffffff]));
 return {algorithm:VERSION,version:1,base,traits:{...base},offsets:{},confidence:{},lastDay:null,watermark:null,memories:[],feedback:[],preferences:{},relationshipDays:[],actions:[],affect:null,mbti:null};
}
function target(base,features,k){
 let driving=0,dialog=0,total=0,quality=0;
 for(const [f,w] of Object.entries(W[k])){const e=features[f];total+=Math.abs(w);if(!e||!finite(e.value)||!finite(e.q))continue;
  const contribution=w*clamp(e.q)*(2*clamp(e.value)-1); if(f[0]==='D')dialog+=contribution;else driving+=contribution;quality+=Math.abs(w)*clamp(e.q);}
 let interaction=0;const pair=k==='curiosity'?['F01','F12',.25]:k==='planning'?['F03','F06',.2]:null;
 if(pair){const[a,b,w]=pair,x=features[a],y=features[b];if(x&&y)interaction=w*Math.min(x.q,y.q)*x.value*y.value;}
 return {value:sig(Math.log(base/(1-base))+clamp(driving+clamp(dialog,-.8,.8)+interaction,-2,2)),q:total?quality/total:0};
}
export function updateTraits(profile,snapshot,now=Date.now()){
 const day=new Date(now+9*3600000).toISOString().slice(0,10);
 if(profile.lastDay===day||profile.watermark===snapshot.watermark)return profile;
 const next=structuredClone(profile);let changed=false;
 for(const k of NAMES){const t=target(profile.base[k],snapshot.global||{},k);next.confidence[k]=t.q;
  if(t.q>0){next.traits[k]=clamp(profile.traits[k]+clamp(.15*t.q*(t.value-profile.traits[k]),-.015,.015));changed=true;}
  for(const[c,features]of Object.entries(snapshot.cells||{})){const ct=target(profile.base[k],features,k);if(ct.q===0)continue;
   const n=snapshot.cellCounts?.[c]||0;if(n<8)continue;const offset=clamp((ct.value-t.value)*n/(n+10),-.15,.15);
   next.offsets[c]??={};const old=next.offsets[c][k]||0;next.offsets[c][k]=old+clamp(offset-old,-.015,.015);}}
 if(changed){next.version++;next.lastDay=day;next.watermark=snapshot.watermark;next.mbti=mbti(next.traits,profile.mbti,day);}
 return changed?next:profile;
}
export function mbti(z,previous=null,day='initial'){
 const axes=[.35*z.initiative+.30*z.social_appetite+.20*z.disclosure+.15*z.expressiveness,.65*z.abstraction+.35*z.curiosity,.5+.5*(z.analytical-z.empathy),.5+.5*(z.planning-z.flexibility)];
 const pos=['E','N','T','J'],neg=['I','S','F','P'];
 const pending=axes.map((v,i)=>{const candidate=v>.58?pos[i]:v<.42?neg[i]:'X',old=previous?.pending?.[i];
  return {candidate,count:candidate!=='X'&&old?.candidate===candidate?(previous?.day===day?old.count:old.count+1):1};});
 const labels=axes.map((v,i)=>{const p=pending[i],old=previous?.label?.[i];return !old||old==='X'?p.candidate:p.candidate!==old&&p.candidate!=='X'&&p.count>=3?p.candidate:old;});
 return {label:labels.join(''),axes,pending,day,boundary:axes.map(v=>v>=.42&&v<=.58)};
}
export function values(z){return {discovery:.65*z.curiosity+.35*z.flexibility,continuity:.65*z.planning+.35*(1-z.flexibility),companionship:.5*z.social_appetite+.3*z.warmth+.2*z.disclosure,care:.55*z.empathy+.45*z.warmth,competence:.55*z.analytical+.45*z.autonomy,self_expression:.55*z.expressiveness+.45*z.autonomy};}
export function makePlan(profile,context={},semantic={},now=Date.now()){
 const days=profile.relationshipDays.length,R=days/(days+10);const cell=context.cell;const z={...profile.traits};
 for(const k of NAMES)z[k]=clamp(z[k]+(profile.offsets[cell]?.[k]||0));
 z.disclosure=clamp(z.disclosure+.25*R);z.expressiveness=clamp(z.expressiveness+.15*R);z.social_appetite=clamp(z.social_appetite+.15*R);
 const s=context.state||{};const num=k=>finite(s[k])?s[k]:null;
 const hunger=num('energyPercent')===null?null:clamp((30-num('energyPercent'))/30);
 const delta=num('actualCabinC')!==null&&num('targetCabinC')!==null?num('targetCabinC')-num('actualCabinC'):null;
 const cold=delta===null?null:clamp((delta-1)/7),heat=delta===null?null:clamp((-delta-1)/7);
 const fatigue=num('tripMinutes')===null?null:clamp((num('tripMinutes')-60)/120);
 const N=finite(context.novelty)?clamp(context.novelty):0;
 const H=semantic.happy??0,U=semantic.tired??0,V=semantic.play??0,Q=semantic.question??0,S=semantic.solve??0;
 const excitementTarget=clamp(.55*z.curiosity*N+.3*H+.15*R);
 const tau=30+150*(1-z.recovery),dt=Math.max(0,(now-(profile.affect?.at||now))/1000);
 const excitement=profile.affect?excitementTarget+(profile.affect.excitement-excitementTarget)*Math.exp(-dt/tau):excitementTarget;
 const need=Math.max(hunger||0,cold||0,heat||0),M=context.memories?.[0]?.score||0;
 const permitted=profile.preferences.teasing===true||V>=.5;
 const recent=profile.actions.slice(-5),penalty=a=>.8*recent.filter(x=>x===a).length/Math.max(1,recent.length);
 const scores={acknowledge:.3+.3*z.warmth+.2*U};
 if(N>=.2||H>=.2)scores.celebrate=1.2*N*z.curiosity+.8*H+.55*z.expressiveness+.25*z.social_appetite-.9*U;
 if(!semantic.quiet&&!profile.preferences.noQuestions&&!recent.slice(-2).every(x=>x==='ask')||recent.length<2&&!semantic.quiet&&!profile.preferences.noQuestions)scores.ask=.65*z.initiative+.65*z.curiosity*N+.3*R-.8*U;
 if(permitted&&U<.4&&!s.safetyWarning)scores.tease=.75*z.playfulness+.55*R+.6*V-.9*U;
 if(U>=.2)scores.care=1.2*U+.6*z.empathy+.35*z.warmth;
 if(S>=.2||Q>=.2)scores.solve=1.3*S+.8*Q+.55*z.analytical+.25*z.planning;
 if(context.preferences?.length)scores.disclose=.65*z.disclosure+.45*z.autonomy+.35*N;
 if(M>=.55)scores.reminisce=1.1*M+.35*z.warmth+.25*R;
 if(semantic.vehicleQuestion||context.proactiveAllowed&&need>=.5)scores.request_need=1.1*need+.3*z.directness;
 if(context.userTurn===false)scores.wait=.65*(1-z.initiative)+.55*(1-z.social_appetite)+.7*U;
 for(const a of Object.keys(scores))scores[a]-=penalty(a);
 let ordered=Object.keys(scores).sort((a,b)=>scores[b]-scores[a]);
 if(Q>=.2||S>=.2)ordered=['solve',...ordered.filter(a=>a!=='solve')];
 if(context.userTurn===false&&(!context.proactiveAllowed||semantic.quiet))ordered=['wait'];
 if(s.safetyWarning===true&&context.warningEvent)ordered=['factual_notice'];
 const primary=ordered[0],secondary=ordered.find(a=>a!==primary&&a!=='wait'&&a!=='request_need'&&a!=='tease'&&a!=='solve'&&a!=='acknowledge'&&a!=='disclose'&&!(primary==='care'&&a==='celebrate'))||null;
 const energy=clamp(.45*z.expressiveness+.35*excitement+.20*z.social_appetite-.45*U-.20*(fatigue||0));
 // A reply may contain several connected thoughts. Sentence budget follows content, not user word count.
 const detailed=semantic.detail>=.5,parked=s.parked===true;
 const maxSentences=parked?(detailed?10:6):(detailed?4:3);
 return {traits:z,values:values(z),relationship:R,state:{hunger,cold,heat,fatigue,excitement},scores,
  primary,secondary,expression:{energy,directness:z.directness,warmth:z.warmth,teasing:permitted?z.playfulness*R*(1-U):0,
   maxQuestions:primary==='ask'||secondary==='ask'?1:0,maxSentences,detail:detailed},affect:{excitement,at:now}};
}
