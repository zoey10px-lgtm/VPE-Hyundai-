/** Controlled counterfactual: same vehicle readings and question, contrasting only HPE style. */
export const COMPARISON_QUESTION =
  "오늘 나랑 드라이브하면 어떤 기분일 것 같아? 네 말투와 개성을 드러내서 짧게 답해 줘.";

const TRAITS = [
  "activity","curiosity","warmth","confidence",
  "caution","playfulness","expressiveness"
];

const flip = {E:"I",I:"E",N:"S",S:"N",T:"F",F:"T",J:"P",P:"J"};
const finite = x => typeof x === "number" && Number.isFinite(x);
const clamp = x => Math.min(1,Math.max(0,x));

export function contrastPersona(context) {
  if (!context || typeof context !== "object" || Array.isArray(context))
    throw new Error("INVALID_PERSONA_CONTEXT");
  const actual=structuredClone(context);
  const contrasted=structuredClone(context);
  // Do not alter observed SDK signals or momentary affect in the control.
  const values=actual.traits || {};
  const traits={...values};
  for(const name of TRAITS){
    if(!finite(values[name])) throw new Error("PERSONA_TRAIT_MISSING");
    traits[name]=clamp(1-clamp(values[name]));
  }
  contrasted.traits=traits;
  if(!Array.isArray(actual.axes) || actual.axes.length!==4 ||
     !actual.axes.every(finite))
    throw new Error("PERSONA_AXES_INVALID");
  contrasted.axes=actual.axes.map(v=>clamp(1-clamp(v)));
  if(typeof actual.mbti!=="string" || !/^[EI][NS][TF][JP]$/.test(actual.mbti))
    throw new Error("PERSONA_MBTI_INVALID");
  contrasted.mbti=[...actual.mbti].map(x=>flip[x]).join("");
  contrasted.comparisonOnly=true;
  return {actual,contrasted};
}
