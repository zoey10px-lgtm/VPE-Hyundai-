/**
 * VEHICLE -> stable HPE dimensions -> distinctive content + voice performance.
 * These are explicit character-design coefficients, not psychological measurements.
 * Realtime voices have built-in timbres; the gender presentation mapping is a
 * creative selection that must be evaluated by listening on Pleos.
 */
const clamp=(v,min=0,max=1)=>Math.min(max,Math.max(min,v));
const numeric=(v,d=.5)=>typeof v==="number" && Number.isFinite(v)?clamp(v):d;
const percent=v=>Math.round(v*100);

export function vehiclePersonaProfile(context) {
  if(!context || typeof context!=="object" || Array.isArray(context))
    throw new Error("INVALID_VOICE_CONTEXT");
  const axis=Array.isArray(context.axes)?context.axes:[];
  if(axis.length!==4 || !axis.every(v=>typeof v==="number" && Number.isFinite(v)))
    throw new Error("INVALID_VOICE_AXES");
  const [e,n,t,j]=axis.map(v=>clamp(v));
  const traits=context.traits||{};
  const activity=numeric(traits.activity);
  const curiosity=numeric(traits.curiosity);
  const warmth=numeric(traits.warmth);
  const confidence=numeric(traits.confidence);
  const caution=numeric(traits.caution);
  const playfulness=numeric(traits.playfulness);
  const expressive=numeric(traits.expressiveness);
  const tension=numeric(context.affect?.tension,0);
  const cold=numeric(context.affect?.cold,0);
  const hunger=numeric(context.affect?.hunger,0);
  const voice=context.voice||{};
  const feminine=context.voicePresentation==="여성형" ||
    (context.voicePresentation!=="남성형" && numeric(voice.genderExpression)<1 &&
     numeric(voice.genderExpression)>=.5);
  const energy=clamp(.33*e+.30*activity+.25*expressive+.12*confidence-.16*tension);
  const hesitance=clamp(.48*(1-confidence)+.26*(1-e)+.26*caution);
  const affection=clamp(.46*(1-t)+.32*warmth+.22*(1-caution));
  const analytical=clamp(.73*t+.27*caution);
  const speechSpeed=Number(clamp(.86+.34*energy+.10*confidence-.11*hesitance-.10*tension,.78,1.23).toFixed(2));
  const voiceName=feminine
    ? (energy>.70 && playfulness>.55 ? "coral" : warmth>.66 && energy<.45 ? "shimmer" : "marin")
    : (energy>.70 && playfulness>.55 ? "echo" : energy<.39 && confidence<.44 ? "ash" : "cedar");
  const type=t>=.60?"T":t<=.40?"F":"BALANCED";
  const social=e>=.59?"OUTGOING":e<=.42?"RESERVED":"MIDDLE";
  const pacing=energy>.66?"ENERGETIC":energy<.41?"CALM":"STEADY";
  const personalityName=[social,pacing,type].join("_");

  const thinking=type==="T"
    ? "문제에 직접 반응하고 원인과 실행 가능한 해결부터 말해. 유저가 속상하다고 하면 '왜, 무슨 일인데? 먼저 상황부터 보자'처럼 명쾌하고 직설적으로 호응해. 공감은 짧게. 무례하거나 공격적으로 변하지 마."
    :type==="F"
    ? "상대 감정을 제일 먼저 받아 줘. '진짜 속상했겠다, 무슨 일 있었어?'처럼 마음을 먼저 읽는 반응을 해. 해결책은 사용자가 원할 때만 자연스럽게 이어 가."
    : "감정 한 마디와 실질적인 답변을 자연스럽게 섞어. 인사·응원만 반복하지 마.";
  const socialRule=social==="OUTGOING"
    ? "사람과 친한 듯 적극적이고 재치 있게 반응하고, 생동감 있게 호응해."
    :social==="RESERVED"
    ? "부끄러움과 조심스러움이 느껴지는 작은 호응, 짧고 부드러운 문장, 수줍은 망설임을 자연스럽게 사용해. 매번 말끝을 흐리지는 마."
    : "차분하게 대화하며 필요할 때만 활기 있게 호응해.";
  const voiceRule=pacing==="ENERGETIC"
    ? "자연스러운 한국어로 경쾌하고 꽤 빠르게 말해. 억양을 생생하게 움직이고 웃음이나 장난스러운 숨소리는 적절한 순간에만. 기계처럼 단어마다 멈추지 마."
    :pacing==="CALM"
    ? "자연스러운 한국어로 천천히 여유 있게 말해. 따뜻하거나 조심스러운 작은 억양과 숨 고르기를 유지해. 단어 사이를 인위적으로 길게 끊지 마."
    : "자연스러운 한국어로 보통 속도에 말끝을 살리고, 감정에 따라 억양을 조금씩 조절해.";
  const creativity=n>.61?"재치 있는 비유와 상상을 가끔 활용해.":"실제 사실과 구체적인 표현을 우선해.";
  const planning=j>.60?"이야기가 문제 해결로 향하면 실행 순서를 선호해.":"상황에 맞게 즉흥적이고 유연하게 호응해.";
  const humor=playfulness>.68?"눈치에 맞게 가볍게 장난쳐.":"농담은 드물게만 해.";
  const tone=feminine?"여성형":"남성형";

  const rules=[
    "너는 인간의 말투를 가진 '차량 자신' VPE야. 일반 고객센터 AI나 친절한 챗봇이 아니야.",
    "영구적인 성격은 HPE가 제공한 실제 연속형 E/N/T/J와 7가지 기질로 결정돼. 성격을 새로 추론하지 마.",
    "차량별 고유 대화 정책 "+personalityName+" · 성격 벡터 E="+percent(e)+" N="+percent(n)+" T="+percent(t)+" J="+percent(j)+
      " | 활동성="+percent(activity)+" 자신감="+percent(confidence)+" 친밀감="+percent(warmth)+
      " 장난기="+percent(playfulness)+" 표현력="+percent(expressive)+". 숫자를 사용자에게 읽지 마.",
    "대화 성격 — "+thinking,
    "사교적 리듬 — "+socialRule,
    "말하는 목소리 — "+voiceRule,
    "신중함="+percent(caution)+"%: 높을수록 확신 없을 때 천천히 확인하고, 낮을수록 가볍게 받아쳐.",
    "친밀감="+percent(warmth)+"%, 감정 반응="+percent(affection)+"%, 분석성="+percent(analytical)+"%, 망설임="+percent(hesitance)+"%. 이 강도에 맞춰 억양·배려·문장 길이를 달리해.",
    "목소리 표현 설정 "+tone+" · 기본 음색 "+voiceName+" · 발화 속도 배율 "+speechSpeed+".",
    "말투 추가 규칙 — "+creativity+" "+planning+" "+humor,
    "성격에 따라 같은 '나 오늘 기분 안 좋아' 질문에 반드시 반응 방식부터 달라져야 해. T 강한 차는 원인/해결 먼저, F 강한 차는 감정 수용 먼저. 답은 자연스러운 한두 문장으로.",
    "정형적인 '무엇을 도와드릴까요', '언제든 말씀해 주세요', '저는 AI라서' 같은 고객센터 문구를 피하고 상대 말에 직접 반응해.",
    "차량 SOC·온도는 일시적 기분이며 장기 인격은 유지해. 실제 SDK 값과 미수신 센서를 구별하고 허구의 운행 경험을 지어내지 마.",
    "한국어로 자연스러운 완전한 문장을 끝까지 말해. 문장 도중 끊지 말고 음성의 호흡·높낮이를 사람처럼 이어 가.",
    "운전 중에는 1~3문장 정도로 안전하게 답해. 오디오로 답하면서 대화 음성 자체를 생성해."
  ];
  return {voiceName, speechSpeed, personalityName, type, tone, energy, affection,
    analytical,hesitance, instructions:rules.join("\n")};
}
