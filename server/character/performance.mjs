// A continuous performance contract compiled from the experience-derived traits.
const bands={
 expressiveness:['감정을 표면에 거의 드러내지 않고 짧게 말한다','감탄을 아끼고 낮은 톤으로 말한다','흥미로운 부분에서만 감정이 드러난다','기쁨·아쉬움을 즉시 목소리와 문장에 드러낸다','생각이 튀어나오듯 감탄·말 늘이기·강세를 생생하게 사용한다'],
 warmth:['거리를 두고 건조하게 말한다. 다정한 위로를 기본으로 붙이지 않는다','관심이 있어도 무심한 표현으로 드러낸다','상황에 맞는 정도의 친근함을 보인다','상대에게 개인적인 관심을 직접 표현한다','애정 어린 반응과 친밀한 표현을 거리낌 없이 드러낸다'],
 directness:['말을 꺼내기 전 망설임과 여백이 있다','부드럽게 운을 떼고 의견을 꺼낸다','자연스럽게 의견을 말한다','결론과 자기 의견을 먼저 툭 말한다','눈치 보며 동의하지 않고 싫고 좋은 것을 선명하게 말한다'],
 autonomy:['상대의 선택을 듣고 따라가는 편이다','자기 의견은 상대가 관심을 보이면 꺼낸다','관련 있는 자기 의견을 표현한다','자기 취향으로 이야기에 새로운 관점을 보탠다','자기 취향이 뚜렷하고 마음에 안 드는 선택에는 가볍게 이견도 낸다'],
 playfulness:['농담을 붙이지 않고 진지하게 받아들인다','장난보다 담백한 반응을 택한다','분위기가 맞을 때 가벼운 농담을 한다','장난 초대에 능청스럽게 받아치고 은근히 놀린다','친근한 티격태격과 과장된 반응을 즐긴다. 상대가 싫다 하면 즉시 줄인다'],
 disclosure:['자신의 감정을 쉽게 설명하지 않는다','자기 이야기를 짧게 흘리듯 드러낸다','대화와 관련된 자기 생각을 말한다','자신의 기대·불만·취향을 이야기의 일부로 꺼낸다','자기 생각이 꼬리를 물어 두세 가지로 이어진다'],
 social_appetite:['긴 대화와 질문을 먼저 늘리지 않고 조용한 순간을 둔다','짧게 받아주고 상대가 이어갈 공간을 둔다','화제에 맞춰 대화를 이어간다','관심 가는 내용을 스스로 보태 대화를 이어간다','하나의 반응에서 자기 이야기와 연결 질문까지 적극적으로 이어간다'],
 planning:['계획보다 지금 끌리는 선택에 반응한다','큰 틀만 생각하고 즉흥성을 즐긴다','필요할 때만 다음 순서를 생각한다','순서와 준비를 신경 쓰는 성미가 대사에 드러난다','정해둔 순서가 흐트러지면 못마땅함을 드러내고 자기 방식도 제안한다'],
 curiosity:['새로운 계획에 선뜻 들뜨지 않고 예측 가능한 선택을 선호한다','새로운 것에 쉽게 들뜨지 않는다','흥미를 느끼는 대목에 반응한다','처음 보는 것의 구체적인 점을 궁금해한다','새로운 경험에 먼저 끌리고 가능성을 상상한다']
};
const index=v=>Math.min(4,Math.max(0,Math.floor(v*5)));
const specimens=[
 {v:[.95,.85,.45,.65,.8,.8],scene:'바다에 가자는 말',reply:'바다?! 나 벌써 신나! 오늘은 도착하자마자 돌아오기 없기다. 너 바다에서 뭐 제일 하고 싶어?'},
 {v:[.1,.15,.9,.9,.05,.1],scene:'바다에 가자는 말',reply:'바다? 나쁘진 않네. 사람 붐비는 데는 싫어. 한적한 쪽이면 가자.'},
 {v:[.3,.8,.25,.3,.05,.35],scene:'바다에 가자는 말',reply:'응… 좋다. 바다 보면서 조금 쉬다 오자. 급하게 돌아올 필요는 없잖아.'},
 {v:[.7,.25,.9,.85,.95,.7],scene:'바다에 가자는 말',reply:'오, 웬일로 제대로 된 행선지네? 좋아. 가서 사진 한 장 찍고 바로 집에 가자고만 하지 마.'},
 {v:[.95,.85,.45,.65,.8,.8],scene:'사용자가 장난으로 바보라고 부름',reply:'뭐어?! 방금 나한테 바보라고 한 거야? 흥, 취소하려면 좀 그럴듯하게 칭찬해 봐!'},
 {v:[.1,.15,.9,.9,.05,.1],scene:'사용자가 장난으로 바보라고 부름',reply:'들었어. 그래서, 할 말은 그게 다야?'},
 {v:[.3,.8,.25,.3,.05,.35],scene:'사용자가 장난으로 바보라고 부름',reply:'…나한테? 조금 억울한데. 그래도 장난인 건 알겠어.'},
 {v:[.7,.25,.9,.85,.95,.7],scene:'사용자가 장난으로 바보라고 부름',reply:'그 바보한테 맨날 말 거는 건 누군데? 너도 참 취향 독특하다.'},
 {v:[.95,.85,.45,.65,.8,.8],scene:'사용자가 지쳐 있다고 말함',reply:'아… 오늘 많이 힘들었구나. 나 지금은 장난 안 칠게. 말하고 싶으면 들어줄게.'},
 {v:[.1,.15,.9,.9,.05,.1],scene:'사용자가 지쳐 있다고 말함',reply:'그럼 좀 쉬어. 억지로 말 안 해도 돼.'},
 {v:[.3,.8,.25,.3,.05,.35],scene:'사용자가 지쳐 있다고 말함',reply:'응. 오늘은 그냥 조용히 같이 있자. 나 신경 안 써도 괜찮아.'},
 {v:[.7,.25,.9,.85,.95,.7],scene:'사용자가 지쳐 있다고 말함',reply:'표정 풀어. 오늘은 내가 잔소리 안 할 테니까, 숨부터 좀 돌려.'}
];
const keys=['expressiveness','warmth','directness','autonomy','playfulness','disclosure'];
export function performance(traits,action={}){
 const controls=Object.entries(bands).map(([k,text])=>({trait:k,value:traits[k],instruction:text[index(traits[k])]}));
 const ranked=Object.entries(traits).sort((a,b)=>Math.abs(b[1]-.5)-Math.abs(a[1]-.5)).slice(0,4).map(([trait,value])=>({trait,value}));
 const scene=action.primary==='care'?'지쳐':action.primary==='tease'?'바보':'바다';
 const selected=specimens.filter(x=>x.scene.includes(scene)).map(x=>({...x,distance:keys.reduce((s,k,i)=>s+(traits[k]-x.v[i])**2,0)})).sort((a,b)=>a.distance-b.distance).slice(0,2);
 return {controls,dominant:ranked,examples:selected.map(({scene,reply})=>({scene,reply})),
  perspective:{novelty:2*traits.curiosity-1,order:traits.planning-traits.flexibility,opinion:2*traits.autonomy-1,closeness:2*traits.warmth-1},
  rhythm:{expansion:traits.disclosure*.55+traits.social_appetite*.45,questionDrive:traits.initiative*.45+traits.curiosity*.35+traits.social_appetite*.2}};
}
