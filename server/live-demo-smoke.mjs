import { randomBytes } from "node:crypto";

const url = process.env.VPE_LIVE_URL || "https://vpe-hyundai.onrender.com";
const username = "vpetest_" + randomBytes(5).toString("hex");
const password = randomBytes(24).toString("base64url");
let token = null;

async function call(path, body, auth = token) {
  const res = await fetch(url + path, {
    method: body === undefined ? "GET" : "POST",
    headers: {
      "Accept": "application/json",
      ...(body !== undefined ? {"Content-Type":"application/json"} : {}),
      ...(auth ? {"Authorization":"Bearer " + auth} : {})
    },
    ...(body !== undefined ? {body:JSON.stringify(body)} : {}),
    signal:AbortSignal.timeout(40000)
  });
  const json=await res.json().catch(()=>({error:"INVALID_JSON_RESPONSE"}));
  return {status:res.status,body:json};
}
function assert(got,expected,label) {
  if(!expected(got))throw new Error(label+" status="+got.status+
    " error="+(got.body.error||"unexpected_response"));
}
function sampleContext() {
  return {
    demo:true,vehicleName:"테스트 시연 차량",mbti:"ENFP",
    axes:[.8,.7,.4,.35],
    traits:{activity:.77,curiosity:.85,warmth:.81,
      confidence:.62,caution:.41,playfulness:.82,expressiveness:.7},
    affect:{hunger:.1,heat:0,cold:0,fatigue:.1,excitement:.7,tension:.1,
      relief:.3,comfort:.6,valence:.6,arousal:.65},
    vehicle:{odometerKm:87320,energyPercent:71},
    memory:{sharedKm:10,meaningfulEvents:1,highlights:[]}
  };
}
async function waitForLiveRevision() {
  for(let i=0;i<45;i++) {
    try {
      const health=await call("/health");
      if(health.status===200 && health.body.apiRevision===2 &&
         health.body.openaiConfigured && health.body.databaseConfigured) {
        console.log("PASS: Render is live with GPT and account DB enabled.");
        return;
      }
    }catch{}
    await new Promise(resolve=>setTimeout(resolve,3000));
  }
  throw new Error("VPE API revision 2 is not deployed or not configured.");
}
let failure;
try {
  await waitForLiveRevision();
  const registered=await call("/api/auth/register",{username,password});
  assert(registered,r=>r.status===201 && typeof r.body.token==="string","register");
  token=registered.body.token;
  console.log("PASS: temporary account registration.");

  const context=sampleContext();
  const saved=await call("/api/vehicles",{
    vehicleKey:"demo-vehicle",vehicleName:"테스트 시연 차량",context
  });
  assert(saved,r=>r.status===200 && r.body.vehicle?.vehicleName==="테스트 시연 차량","vehicle");
  console.log("PASS: demo vehicle HPE context persisted.");

  const answer=await call("/api/chat",{
    vehicleKey:"demo-vehicle",vehicleName:"테스트 시연 차량",
    context,message:"안녕! 너의 성격에 맞게 한 문장으로 인사해줘."
  });
  assert(answer,r=>r.status===200 && typeof r.body.reply==="string" &&
    r.body.reply.length>0,"GPT demo response");
  console.log("PASS: live OpenAI demo chat returned a nonempty response.");

  const history=await call("/api/messages?vehicleKey=demo-vehicle");
  assert(history,r=>r.status===200 && r.body.messages?.length===2 &&
    r.body.messages[0].role==="user" && r.body.messages[1].role==="assistant","history");
  const isolated=await call("/api/messages?vehicleKey=demo-other");
  assert(isolated,r=>r.status===200 && r.body.messages?.length===0,"isolation");
  console.log("PASS: per-vehicle message history and isolation.");
}catch(err){
  failure=err;
  console.error("FAIL: "+String(err?.message||err));
}finally{
  if(token) {
    try {
      const deleted=await call("/api/auth/delete",{password,confirm:"DELETE_MY_ACCOUNT"});
      if(deleted.status===200 && deleted.body.deleted===true) {
        console.log("PASS: temporary account, vehicles and messages deleted.");
      }else {
        console.error("WARNING: test account cleanup failed. status="+deleted.status+
          " code="+(deleted.body.error||"unknown"));
        failure ||= new Error("test account cleanup failed");
      }
    }catch(err){
      console.error("WARNING: test account cleanup failed: "+(err?.message||err));
      failure ||= err;
    }
  }
}
if(failure)process.exitCode=1;
