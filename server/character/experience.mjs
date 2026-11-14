// Creative vehicle-character model, not a diagnosis of the driver.
// All coordinates come from observed aggregates; vehicle ID contributes no temperament.
import {clamp,finite} from './engine.mjs';
export const FACTORS={exploration:{F01:.55,F02:.45},routine:{F03:.5,F05:.2,F06:.3},variety:{F04:.65,F10:.35},social:{F07:1},endurance:{F09:.6,F11:.4},night:{F08:1}};
export const LOADINGS={
 initiative:[.8,-.35,.5,.8,-.2,-.1],expressiveness:[.7,-.6,.8,.65,-.2,-.4],
 disclosure:[.4,-.2,.4,.9,-.4,-.2],social_appetite:[.3,-.15,.3,1.2,-.5,-.4],
 curiosity:[1.25,-.35,.65,.1,.2,.1],abstraction:[.65,-.6,.55,.1,.2,.2],
 analytical:[-.25,.8,-.35,-.3,.75,.1],empathy:[.1,.15,-.1,.9,.05,-.1],
 planning:[-.5,1.2,-.65,.1,.35,-.1],flexibility:[.65,-.85,1,.15,.05,.15],
 playfulness:[.65,-.7,.85,.55,-.2,-.15],directness:[-.15,.65,-.15,-.65,.8,.15],
 warmth:[.15,-.2,.15,1.1,-.35,-.25],autonomy:[.6,.15,.45,-.8,.8,.25],
 reactivity:[.65,-.65,.85,.35,-.25,-.3],recovery:[.35,.3,.5,.15,.55,-.2]
};
export function experienceTarget(features={}){
 const factors=Object.fromEntries(Object.entries(FACTORS).map(([k,weights])=>{
  const present=Object.entries(weights).filter(([f])=>finite(features[f]?.value)&&finite(features[f]?.q));
  const support=present.reduce((s,[f,w])=>s+w*clamp(features[f].q),0);
  return [k,{value:support?present.reduce((s,[f,w])=>s+w*clamp(features[f].q)*clamp(features[f].value),0)/support:.5,q:support,
   evidence:present.map(([f])=>({feature:f,...features[f]}))}];
 }));
 const vector=Object.values(factors).map(f=>(2*f.value-1)*f.q);
 // Cross-feature terms create combinations rather than discrete character classes.
 const [e,r,v,s,d,n]=vector,interactions={curiosity:.5*e*v,planning:.5*r*(1-v)/2,playfulness:.4*v*s,autonomy:.4*d*(1-s)/2,expressiveness:.3*e*s};
 const traits={},confidence={};
 for(const [k,weights] of Object.entries(LOADINGS)){
  const score=weights.reduce((sum,w,i)=>sum+w*vector[i],0)+(interactions[k]||0);
  traits[k]=clamp(1/(1+Math.exp(-1.7*score)),.06,.94);
  confidence[k]=weights.reduce((sum,w,i)=>sum+Math.abs(w)*Object.values(factors)[i].q,0)/weights.reduce((sum,w)=>sum+Math.abs(w),0);
 }
 return {traits,confidence,factors};
}
