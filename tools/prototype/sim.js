/* ================= seeded RNG ================= */
function xmur3(str){let h=1779033703^str.length;for(let i=0;i<str.length;i++){h=Math.imul(h^str.charCodeAt(i),3432918353);h=h<<13|h>>>19;}return function(){h=Math.imul(h^(h>>>16),2246822507);h=Math.imul(h^(h>>>13),3266489909);return (h^=h>>>16)>>>0;};}
function mulberry32(a){return function(){a|=0;a=(a+0x6D2B79F5)|0;let t=Math.imul(a^(a>>>15),1|a);t=(t+Math.imul(t^(t>>>7),61|t))^t;return ((t^(t>>>14))>>>0)/4294967296;};}
function makeRng(seed,salt){
  const f=mulberry32(xmur3(seed+'::'+salt)());
  return {
    f,int:(a,b)=>a+Math.floor(f()*(b-a+1)),pick:arr=>arr[Math.floor(f()*arr.length)],chance:p=>f()<p,
    shuffle(arr){const a=arr.slice();for(let i=a.length-1;i>0;i--){const j=Math.floor(f()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;},
    weighted(list){const tot=list.reduce((s,x)=>s+Math.max(0,x.w),0);if(tot<=0)return null;let r=f()*tot;for(const x of list){r-=Math.max(0,x.w);if(r<=0)return x;}return list[list.length-1];}
  };
}

/* ================= content ================= */
const BLOCKS=[["deepslate","Deepslate"],["amethyst","Amethyst"],["kelp","Kelp"],["wheat","Wheat"],["glass","Glass"],["bone_block","Bone"],["copper","Copper"],["moss","Moss"],["obsidian","Obsidian"],["coral","Coral"],["honeycomb","Honeycomb"],["snow","Snow"],["iron_block","Iron"],["sculk","Sculk"],["cobweb","Cobwebs"],["candle","Candles"],["mud","Mud"],["prismarine","Prismarine"]];
const ENTITIES=[["wolf","Wolves"],["bee","Bees"],["drowned","the Drowned"],["enderman","Endermen"],["skeleton","Skeletons"],["fox","Foxes"],["axolotl","Axolotls"],["spider","Spiders"],["phantom","Phantoms"],["sheep","Sheep"],["bat","Bats"],["witch","Witches"]];
const ACTIONS=[["mine_ore","tore ore from the deep places"],["fell_tree","felled the old trees"],["till_soil","broke the earth for planting"],["dig_deep","dug toward the floor of the world"],["kill_passive","slaughtered gentle beasts"],["kill_hostile","hunted the things of the night"],["breed_animals","multiplied the herds"],["craft_tool","forged new tools"],["brew_potion","brewed strange draughts"],["enchant_item","bound words into iron"],["burn_item","fed offerings to the fire"],["light_fire","set the land alight"],["extinguish_fire","drowned the sacred fires"],["sleep_in_nether","slept in the burning places"],["build_height","built toward the sky"]];
const EPITHETS=["Warden","Hunger","Lantern","Mother","Father","King","Queen","Witness","Tongue","Hand","Saint","Vessel","Mouth","Thorn","Keeper","Widow","Weaver","Shepherd","Judge","Heart","Eye","Smith","Herald","Beast"];
const G_ADJ=["Smothering","Silent","Hollow","Ashen","Patient","Gray","Bitter","Last","Unlit","Sunken","Knotted","Weeping"];
const G_NOUN=["Tree","Chalice","Hand","Choir","Thorn","Lantern","Knot","Veil","Ledger","Door","Bell","Root"];
const SECLUDE=["to the summit of the Mountain","into the house on the mountainside","into the deepest Woods"];
const MOUNTAIN_PLACES=["at the Mountain's summit","in a cave on the Mountain's eastern face","beneath a cairn on the Mountain's northern slope"];
const HOUSE_PLACES=["behind the hearth of the House","beneath the House's cellar stair","in the House's locked attic"];
const WOODS_PLACES=["at the hollow oak in the deep Woods","by the black pool in the Woods","under the fallen stones at the Woods' edge"];
const TRAITS_DEF={
  secretive:"Hides all knowledge of itself. Its very existence is a secret.",
  obfuscator:"Works to keep mortals from correct knowledge of the Altus.",
  historian:"Never lies, and works to see every event recorded truly.",
  mad:"Sometimes acts against its own motives.",
  vengeful:"Holds grudges long and lets them grow.",
  forgiving:"Lets wrongs go.",
  proud:"Belittles the weak and inflates its own deeds.",
  ambitious:"Seeks power, taken from the weak if need be.",
  loyal:"Stands by its groups.",
  opportunist:"Follows power: changes groups and sides to stand with the strongest.",
  zealous:"Treats its dislikes as crimes, and punishes whoever commits them.",
  reclusive:"Withdraws from the others.",
  gregarious:"Seeks company and founds groups.",
  paranoid:"Suspects everyone.",
  protective:"Defends its friends and children.",
  devourer:"Hungers for the deaths of weaker gods."
};
const TRAIT_NAMES=Object.keys(TRAITS_DEF);
const RARE=new Set(["mad","devourer"]);
const EXCL=[["historian","obfuscator"],["historian","secretive"],["vengeful","forgiving"],["loyal","opportunist"],["reclusive","gregarious"],["protective","devourer"]];
const TELLS={proud:"Mark it well; there is no doubt in it.",secretive:"Ask no more of this.",vengeful:"Remember who did this.",opportunist:"It is as the strongest tell it.",loyal:"I would swear to it beside my allies.",forgiving:"Let it rest now.",obfuscator:"Seek no further; there is nothing more to find.",paranoid:"Trust no other account.",zealous:"So it was, and so it is written."};
const ORIGIN_LABEL={glory:"god-from-glory",gods:"god-from-god",corpse:"god-from-corpse",nowhere:"god-from-nowhere"};
const YEARS=300, ACT_P=0.07;

const key=l=>l.cat+':'+l.id;
const lbl=l=>l.cat==='action'?l.id:l.label;
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const ord=n=>{const s=["th","st","nd","rd"],v=n%100;return n+(s[(v-20)%10]||s[v]||s[0]);};
const Y=y=>'Y'+String(y).padStart(3,'0');
const cap=s=>s.charAt(0).toUpperCase()+s.slice(1);
const lc=s=>s.charAt(0).toLowerCase()+s.slice(1);
const poss=s=>/s$/.test(s)?s+"'":s+"'s";
function actPhrase(item){if(item.cat==='block')return `raised a shrine of ${item.label}`;if(item.cat==='entity')return `loosed ${item.label} upon the world`;return item.verb;}
const shared=(a,b)=>a.likes.filter(l=>b.likes.some(m=>key(m)===key(l)));
const conflictItems=(a,b)=>a.likes.filter(l=>b.dislikes.some(m=>key(m)===key(l)));
function principlePhrase(p,S){
  const n=id=>S.gods[id].name;
  switch(p.kind){
    case 'OPPOSE':return `to oppose ${n(p.target)}`;
    case 'KILL':return `to kill ${n(p.target)}`;
    case 'AVENGE':return p.known?`to avenge ${n(p.dead)} upon ${S.names(p.killers)}`:`to find and punish the slayers of ${n(p.dead)}`;
    case 'GUARD':return 'to guard a hidden truth';
    case 'PROTECT':return `to protect ${n(p.protect)}`;
    case 'RECORD':return 'to keep the true record of all that happens';
    case 'VEIL':return 'to veil the Altus from mortal eyes';
  }
  return '';
}

/* ================= simulation ================= */
function simulate(seed){
  const R=makeRng(seed,'history');
  const S={seed,gods:[],events:[],lies:[],log:[],groups:[],grudges:[],wars:[],secrets:[],story:{},regions:[],tensions:[],deicides:[],YEARS};
  const {gods,events,lies,log,groups,grudges,wars,secrets,story}=S;
  let evc=0,liec=0,grc=0,secc=0;
  const pendingCorpses=[],opMap=new Map(),usedNames=new Set(),usedGroupNames=new Set();
  const nm=id=>gods[id].name;
  const names=ids=>{const a=ids.map(nm);if(a.length<=1)return a.join('');if(a.length===2)return a.join(' and ');return a.slice(0,-1).join(', ')+', and '+a[a.length-1];};
  S.names=names;S.nm=nm;
  const L=(year,tag,text,cls,cat,indent=0)=>log.push({year,tag,text:cap(text),cls,cat,indent});
  const newEvent=(type,year,actors,targets,data={})=>{const ev={id:'E-'+String(++evc).padStart(4,'0'),type,year,actors,targets,data,secretRef:null};events.push(ev);return ev;};
  const T=(g,t)=>(g&&g.traits[t])||0;
  const alive=()=>gods.filter(g=>g.alive);
  const active=(g,y)=>g.alive&&g.secludedUntil<y;
  const groupsOf=id=>groups.filter(gr=>!gr.dissolved&&gr.members.includes(id));
  const gname=gid=>groups[gid].name;
  const atWar=id=>wars.some(w=>!w.over&&(w.sideA.includes(id)||w.sideB.includes(id)));
  const sumP=list=>list.reduce((s,g)=>s+g.power,0);
  const kin=(a,b)=>a!==b&&(gods[a].creator===b||gods[b].creator===a||gods[a].sourceCorpse===b||gods[b].sourceCorpse===a);
  function aggressorsOf(id,year){const seen=new Set(),out=[];
    for(const e of events){if(year-e.year>40||e.year>year)continue;if(!['offense','strike','kill_attempt'].includes(e.type)||!e.targets.includes(id)||e.secretRef)continue;
      for(const a of e.actors)if(a!==id&&gods[a].alive&&!seen.has(a)){seen.add(a);out.push(gods[a]);}}
    return out;}

  /* ---- opinions ---- */
  function base(a,b){const A=gods[a],B=gods[b];let v=2*shared(A,B).length-2.5*conflictItems(B,A).length;if(A.creator===b||B.creator===a)v+=3;if(A.sourceCorpse===b)v+=2;return clamp(Math.round(v),-8,8);}
  const getOp=(a,b)=>{const k=a+'|'+b;if(!opMap.has(k))opMap.set(k,base(a,b));return opMap.get(k);};
  const setOp=(a,b,v)=>opMap.set(a+'|'+b,clamp(v,-10,10));
  const bump=(a,b,d)=>{if(a!==b)setOp(a,b,getOp(a,b)+d);};
  const friend=(a,b)=>{if(a===b)return 0;const o=getOp(a,b);return o>=3?Math.min(10,Math.round(o)):0;};
  S.getOp=getOp;

  /* ---- grudges ---- */
  function grudgeVs(h,t){let best=null;for(const g of grudges){if(g.resolved||g.holder!==h)continue;let hit=false;if(g.tt==='god')hit=g.target===t;else{const gr=groups[g.target];hit=!gr.dissolved&&gr.members.includes(t)&&!g.forgiven.has(t);}if(hit&&(!best||g.sev>best.sev))best=g;}return best;}
  const grudgeScore=(h,t)=>{const g=grudgeVs(h,t);return g?g.sev:0;};
  const groupGrudge=(h,gid)=>grudges.find(g=>!g.resolved&&g.holder===h&&g.tt==='group'&&g.target===gid);
  const tlabel=(tt,t)=>tt==='god'?nm(t):'group '+gname(t);
  function addGrudge(h,tt,t,sev,ev,year,indent=1,reason=null){
    if(tt==='god'&&h===t)return null;if(!gods[h].alive)return null;if(tt==='god'&&!gods[t].alive)return null;
    sev=clamp(Math.round(sev),1,10);
    const ex=grudges.find(g=>!g.resolved&&g.holder===h&&g.tt===tt&&g.target===t);
    if(ex){const old=ex.sev;ex.sev=Math.min(10,ex.sev+Math.ceil(sev/2));if(ex.sev!==old)L(year,'',`${ex.id} deepens: ${nm(h)} → ${tlabel(tt,t)} sev=${ex.sev}`,'grudge','conflict',indent);return ex;}
    const g={id:'G-'+String(++grc).padStart(3,'0'),holder:h,tt,target:t,sev,cause:ev?ev.id:null,year,resolved:null,forgiven:new Set()};
    grudges.push(g);L(year,'',`GRUDGE ${g.id} ${nm(h)} → ${tlabel(tt,t)} sev=${sev}${ev?' cause='+ev.id:''}${reason?'  ('+reason+')':''}`,'grudge','conflict',indent);g.reason=reason;return g;
  }

  /* ---- secrets ---- */
  function addSecret(o,year,indent=1,quiet=false){
    const s={id:'S-'+String(++secc).padStart(3,'0'),kind:o.kind,about:o.about,text:cap(o.text),keepers:new Set(o.keepers),knowers:new Set(o.knowers||[]),importance:clamp(o.importance,1,10),exposed:false,exposer:null,eventRef:o.eventRef||null,group:o.group??null,threats:new Set(),year};
    secrets.push(s);
    if(o.eventRef){const ev=events.find(e=>e.id===o.eventRef);if(ev&&!ev.secretRef)ev.secretRef=s.id;}
    if(!quiet)L(year,'SECRET',`${s.id} ${s.kind}: “${s.text}”  kept by ${names([...s.keepers])}  importance=${s.importance}`,'lie','secrets',indent);
    return s;
  }
  const importanceFor=(g,s)=>Math.min(10,s.importance+(T(g,'secretive')?2:0));
  const purposeSecret=gr=>secrets.find(s=>s.kind==='GROUP_PURPOSE'&&s.group===gr.id);

  /* ---- lies ---- */
  function presence(c,year,exId){
    const w=wars.find(w=>(w.sideA.includes(c)||w.sideB.includes(c))&&w.start<=year&&(!w.over||w.endYear>=year));
    if(w)return {desc:`at war that year (${w.eventId})`,ref:w.eventId};
    const s=gods[c].seclusions.find(s=>s.start<=year&&s.end>=year);
    if(s)return {desc:`in seclusion that year (${s.ev})`,ref:s.ev};
    const e=events.find(e=>e.id!==exId&&e.year===year&&(e.actors.includes(c)||e.targets.includes(c)));
    if(e)return {desc:`busy elsewhere that year (${e.id})`,ref:e.id};
    if(gods[c].born>year)return {desc:`not yet born that year`,ref:null};
    return null;
  }
  function scapegoat(ev,teller,pref){
    const excl=new Set([...ev.actors,...ev.targets,teller]);
    const order=[...pref.filter(x=>!excl.has(x)),...R.shuffle(gods.map(g=>g.id)).filter(x=>!excl.has(x)&&!pref.includes(x))];
    for(const c of order){if(!gods[c].alive)continue;const p=presence(c,ev.year,ev.id);if(p)return {id:c,...p};}
    return null;
  }
  const storyReveal=(ev,lie)=>ev.type==='location'?'LOCATION':lie.distortion==='CHANGE_CAUSE'?'WHY':'WHO';
  function makeLie(ev,teller,motive,distortion,claimed,targetGod,contra,year,opt={}){
    const tellers=(opt.tellers||[teller]).filter(t=>!T(gods[t],'historian')&&!story[ev.id+'|'+t]);
    if(!tellers.length)return null;
    const lie={id:'L-'+String(++liec).padStart(3,'0'),eventId:ev.id,teller:tellers[0],tellers,motive,distortion,claimed,targetGod,contra,coordinated:opt.group??null,exposed:false,year};
    lies.push(lie);for(const t of tellers)story[ev.id+'|'+t]=lie;
    L(year,opt.group!=null?'COORDINATED':'LIE',`${lie.id} ${opt.group!=null?`${gname(opt.group)} agrees on one story (${names(tellers)})`:'told by '+nm(tellers[0])}  motive=${motive}  distortion=${distortion}  about ${ev.id}`,'lie','secrets',opt.indent??1);
    L(year,'',`official story: “${renderEvent(ev,storyReveal(ev,lie),lie,S)}”`,'lie','secrets',(opt.indent??1)+1);
    L(year,'',`can be caught: ${contra}`,'meta','secrets',(opt.indent??1)+1);
    return lie;
  }

  /* ---- motivation contests ---- */
  const mlabel=m=>m.score===Infinity?`${m.type} (absolute)`:`${m.type}${m.trait?':'+m.trait:''} ${m.score}`;
  function decide(g,driver,opps,year,what){
    const top=opps.filter(o=>o&&o.score>0).sort((a,b)=>b.score-a.score)[0];
    if(!top)return true;
    const win=top.score!==Infinity&&(driver.score>top.score||(driver.score===top.score&&R.chance(.5)));
    if(!win)L(year,'RESTRAINED',`${g.name} wanted to ${what} (${mlabel(driver)}) but ${mlabel(top)} won${driver.score===top.score?' (tie, chance)':''}`,'meta','motive');
    else if(driver.score===top.score)L(year,'TIE',`${g.name}: ${mlabel(driver)} equals ${mlabel(top)}; chance favors acting`,'meta','motive');
    return win;
  }
  function oppHarm(g,t){const o=[];const f=friend(g.id,t.id);if(f)o.push({type:'FRIENDSHIP',score:f});for(const gr of groupsOf(g.id))if(gr.members.includes(t.id))o.push({type:'LOYALTY',score:gr.loyalty[g.id]});return o;}
  const historianOpp=g=>T(g,'historian')?[{type:'TRAIT:historian',score:Infinity}]:[];

  /* ---- generation helpers ---- */
  function godName(likes){
    const anchors=R.shuffle(likes.filter(l=>l.cat!=='action')),eps=R.shuffle(EPITHETS);
    for(const a of anchors)for(const e of eps){const nme=`the ${e} of ${a.label}`;if(!usedNames.has(nme)){usedNames.add(nme);return nme;}}
    const a=anchors[0],e=eps[0];let k=2;while(usedNames.has(`the ${e} of ${a.label} the ${ord(k)}`))k++;const nme=`the ${e} of ${a.label} the ${ord(k)}`;usedNames.add(nme);return nme;
  }
  const compatible=(t,name)=>!EXCL.some(p=>p.includes(name)&&p.some(x=>x!==name&&x in t));
  function genTraits(n,inherit=[]){
    const t={};
    for(const [name,v] of inherit){if(Object.keys(t).length>=n)break;if(name!=='progenitor'&&!(name in t)&&compatible(t,name))t[name]=clamp(v+R.int(-1,1),2,9);}
    for(const name of R.shuffle(TRAIT_NAMES)){if(Object.keys(t).length>=n)break;if(RARE.has(name)&&R.chance(.6))continue;if(!(name in t)&&compatible(t,name))t[name]=R.int(3,9);}
    return t;
  }
  function genLikes(from=null){
    const mb=b=>({cat:'block',id:b[0],label:b[1]}),me=e=>({cat:'entity',id:e[0],label:e[1]}),ma=a=>({cat:'action',id:a[0],label:a[0],verb:a[1]});
    const likes=[];const has=l=>likes.some(m=>key(m)===key(l));
    if(from)for(const l of R.shuffle(from.likes).slice(0,R.int(1,2)))likes.push(l);
    const need={block:R.int(2,3),entity:R.int(1,2),action:R.int(2,3)};
    const pools={block:R.shuffle(BLOCKS).map(mb),entity:R.shuffle(ENTITIES).map(me),action:R.shuffle(ACTIONS).map(ma)};
    for(const c of ['block','entity','action']){let have=likes.filter(l=>l.cat===c).length;for(const l of pools[c]){if(have>=need[c])break;if(!has(l)){likes.push(l);have++;}}}
    const dislikes=[];
    for(const c of ['block','entity','action']){let k=R.int(1,2);for(const l of pools[c]){if(k<=0)break;if(!has(l)&&!dislikes.some(m=>key(m)===key(l))&&!(from&&from.likes.some(m=>key(m)===key(l)))){dislikes.push(l);k--;}}}
    return {likes,dislikes};
  }
  function makeGod(o,year){
    const id=gods.length;const {likes,dislikes}=o.lk;
    const anchor=likes.find(l=>l.cat!=='action');
    const g={id,name:godName(likes),origin:o.origin,creator:o.creator??null,sourceCorpse:o.sourceCorpse??null,traits:o.traits,likes,dislikes,power:o.power,startPower:o.power,born:year,alive:true,deathYear:null,killers:[],secludedUntil:-1,seclusions:[],progenitor:0};
    gods.push(g);return g;
  }
  function conflictGrudges(c,year){
    for(const o of alive()){
      if(o===c)continue;
      for(const [holder,liker] of [[c,o],[o,c]]){
        const items=conflictItems(liker,holder);
        if(!items.length)continue;
        const sev=clamp(Math.round(1.5+1.5*items.length+(T(holder,'vengeful')?2:0)-(T(holder,'forgiving')?1:0)),1,8);
        const gg=addGrudge(holder.id,'god',liker.id,sev,null,year,1,`${liker.name} likes ${items.map(lbl).join(' and ')}, which ${holder.name} despises`);
        if(gg&&!gg.items)gg.items=items;
      }
    }
  }
  function birthNotes(g,year){
    L(year,'',`traits: ${Object.entries(g.traits).map(([t,v])=>t+' '+v).join(', ')}`,'meta','births',1);
    conflictGrudges(g,year);
    if(T(g,'secretive'))addSecret({kind:'EXISTENCE',about:g.id,text:`${g.name} exists, though it hides itself from every record.`,keepers:[g.id],importance:T(g,'secretive')},year);
  }

  /* ---- origins ---- */
  function birthGlory(year){
    const g=makeGod({origin:'glory',power:10,traits:genTraits(R.int(1,3)),lk:genLikes()},year);
    g.progenitor=R.int(3,5);
    const ev=newEvent('glory_birth',year,[g.id],[]);
    L(year,'GLORY',`${ev.id} ${g.name} came from glory and made the world (god-from-glory, power 10)`,'birth','births');
    birthNotes(g,year);
  }
  function createGod(cr,reason,year,mot,extra={}){
    const p=Math.floor(cr.power/10);if(p<1)return null;
    const inh=R.shuffle(Object.entries(cr.traits)).slice(0,R.int(1,2));
    const c=makeGod({origin:'gods',creator:cr.id,power:p,traits:genTraits(R.int(1,3),inh),lk:genLikes(cr)},year);
    const ev=newEvent('creation',year,[cr.id],[c.id],{reason,target:extra.target??null});
    setOp(c.id,cr.id,7);setOp(cr.id,c.id,6);
    L(year,'CREATION',`${ev.id} ${cr.name} made ${c.name} (god-from-god, power ${p})  reason=${reason}  [${mot}]`,'birth','births');
    birthNotes(c,year);
    if(reason==='champion'&&extra.target!=null)addGrudge(c.id,'god',extra.target,extra.sev||6,ev,year);
    if(reason==='keeper'&&extra.secret){extra.secret.keepers.add(c.id);L(year,'',`${c.name} now keeps ${extra.secret.id}`,'lie','secrets',1);}
    if(cr.progenitor>0)cr.progenitor--;
    if(T(cr,'secretive')&&reason!=='progenitor'&&R.chance(.5))addSecret({kind:'DEED',about:cr.id,text:`${cr.name} made ${c.name}.`,keepers:[cr.id,c.id],importance:T(cr,'secretive'),eventRef:ev.id},year);
    return c;
  }
  function emergeCorpse(pc,year){
    const d=gods[pc.dead];
    const c=makeGod({origin:'corpse',sourceCorpse:d.id,power:2*pc.power,traits:genTraits(R.int(1,3),R.shuffle(Object.entries(d.traits)).slice(0,R.int(1,2))),lk:genLikes(d)},year);
    const ev=newEvent('corpse_birth',year,[c.id],[d.id],{killers:pc.killers});
    L(year,'CORPSE',`${ev.id} ${c.name} rose from the corpse of ${d.name} (god-from-corpse, power ${c.power})`,'birth','births');
    birthNotes(c,year);
    if(R.chance(.8))for(const k of pc.killers)if(gods[k].alive)addGrudge(c.id,'god',k,R.int(6,9),ev,year);
    const mourners=alive().filter(o=>o!==c&&(friend(o.id,d.id)>=4||kin(o.id,d.id))&&!pc.killers.includes(o.id));
    if(mourners.length&&pc.killers.some(k=>gods[k].alive)&&R.chance(.6))foundGroup([c,...mourners.slice(0,3)],{kind:'AVENGE',dead:d.id,killers:pc.killers.slice(),known:true,target:pc.killers.find(k=>gods[k].alive)??null,secretId:null},year,`rose to avenge ${d.name}`);
    if(pc.secret){pc.secret.knowers.add(c.id);L(year,'',`${c.name} remembers who killed ${d.name}; it now knows ${pc.secret.id}`,'lie','secrets',1);}
  }
  function birthNowhere(year){
    const ps=alive().map(g=>g.power);const p=R.int(Math.min(...ps),Math.max(...ps));
    const c=makeGod({origin:'nowhere',power:p,traits:genTraits(R.int(1,3)),lk:genLikes()},year);
    const ev=newEvent('nowhere_birth',year,[c.id],[]);
    L(year,'NOWHERE',`${ev.id} ${c.name} came from nowhere (god-from-nowhere, power ${p})`,'birth','births');
    birthNotes(c,year);
  }

  /* ---- motivations ---- */
  function motivations(g){
    const M=[];
    if(g.progenitor>0)M.push({type:'TRAIT',trait:'progenitor',score:9});
    for(const gd of grudges){if(gd.resolved||gd.holder!==g.id)continue;if(gd.tt==='god'&&!gods[gd.target].alive)continue;if(gd.tt==='group'&&groups[gd.target].dissolved)continue;M.push({type:'GRUDGE',score:gd.sev,grudge:gd});}
    for(const o of alive())if(o!==g){const f=friend(g.id,o.id);if(f)M.push({type:'FRIENDSHIP',score:f,target:o});}
    for(const [t,v] of Object.entries(g.traits))if(t!=='mad')M.push({type:'TRAIT',trait:t,score:v});
    for(const gr of groupsOf(g.id))M.push({type:'LOYALTY',score:gr.loyalty[g.id],group:gr});
    for(const s of secrets)if(!s.exposed&&s.keepers.has(g.id))M.push({type:'SECRET',score:importanceFor(g,s),secret:s});
    return M;
  }
  function godAct(g,year){
    const mad=T(g,'mad');
    if(mad&&R.chance(.15*mad/9))return madAct(g,year);
    const M=motivations(g);if(!M.length)return;
    const d=R.weighted(M.map(m=>({w:m.score*m.score*(m.type==='FRIENDSHIP'?.35:1),m}))).m;
    switch(d.type){
      case 'GRUDGE':return actGrudge(g,d,year);
      case 'FRIENDSHIP':return actFriend(g,d,year);
      case 'TRAIT':return actTrait(g,d,year);
      case 'LOYALTY':return council(d.group,year,g,d);
      case 'SECRET':return actSecret(g,d,year);
    }
  }

  /* ---- basic acts ---- */
  function coverUp(ev,doer,year){
    if(T(doer,'historian'))return;
    const s=T(doer,'secretive');
    if(s&&R.chance(.6)){
      const sec=addSecret({kind:'DEED',about:doer.id,text:renderEvent(ev,'WHO',null,S),keepers:[doer.id],knowers:ev.targets.filter(t=>gods[t].alive),importance:s,eventRef:ev.id},year);
      const gt=grudges.filter(g=>!g.resolved&&g.holder===doer.id&&g.tt==='god'&&gods[g.target].alive&&!ev.targets.includes(g.target)).map(g=>g.target);
      const sg=scapegoat(ev,doer.id,gt);
      if(sg&&grudgeScore(doer.id,sg.id)>friend(doer.id,sg.id))makeLie(ev,doer.id,'KEEP_SECRET','SWAP_ACTOR',{actor:sg.id},sg.id,`${nm(sg.id)} was ${sg.desc}`,year);
      else makeLie(ev,doer.id,'KEEP_SECRET','OMIT_ACTOR',{actor:null},null,`${names(ev.targets)} saw who did it`,year);
      return sec;
    }
    if(R.chance(.18)){
      const gt=grudges.filter(g=>!g.resolved&&g.holder===doer.id&&g.tt==='god'&&gods[g.target].alive&&!ev.targets.includes(g.target)).map(g=>g.target);
      if(gt.length){const sg=scapegoat(ev,doer.id,gt);
        if(sg&&gt.includes(sg.id)){const d={type:'GRUDGE',score:grudgeScore(doer.id,sg.id)};if(decide(doer,d,oppHarm(doer,gods[sg.id]),year,`blame ${nm(sg.id)}`))makeLie(ev,doer.id,'SMEAR_RIVAL','SWAP_ACTOR',{actor:sg.id},sg.id,`${nm(sg.id)} was ${sg.desc}`,year);}}
    }else if(T(doer,'proud')&&R.chance(.25)&&ev.targets.length)makeLie(ev,doer.id,'INFLATE_DEEDS','CHANGE_CAUSE',{cause:`${poss(nm(ev.targets[0]))} insolence demanded it`},null,`${poss(nm(ev.targets[0]))} account records no provocation`,year);
    return null;
  }
  function offense(a,b,year,mot){
    const ci=conflictItems(a,b);if(!ci.length)return;
    const item=R.pick(ci);const ev=newEvent('offense',year,[a.id],[b.id],{item});bump(b.id,a.id,-3);
    L(year,'OFFENSE',`${ev.id} ${a.name} ${actPhrase(item)}, which ${b.name} despises  [${mot}]`,'event','conflict');
    const sec=coverUp(ev,a,year);
    if(!sec)addGrudge(b.id,'god',a.id,R.int(3,6)+(T(b,'vengeful')?2:0),ev,year);
    else addGrudge(b.id,'god',a.id,R.int(2,4),ev,year);
  }
  function strike(atts,t,year,mot,gr){
    if(!t.alive||!atts.length)return;
    const ids=atts.map(a=>a.id),pa=sumP(atts),pt=t.power,ok=R.f()<pa/(pa+pt);
    const ev=newEvent('strike',year,ids,[t.id],{success:ok,group:gr?gr.id:null});
    const who=gr?`${gr.name} (${names(ids)})`:atts[0].name;
    L(year,'STRIKE',`${ev.id} ${who} ${ok?'struck and wounded':'struck at, and failed to hurt,'} ${t.name}  power ${pa} vs ${pt}  [${mot}]`,'war','conflict');
    if(ok){t.power=Math.max(1,t.power-1);if(gr||T(atts[0],'ambitious')||T(atts[0],'devourer')){atts[0].power+=1;L(year,'',`power: ${atts[0].name} +1, ${t.name} −1`,'meta','conflict',1);}else L(year,'',`power: ${t.name} −1`,'meta','conflict',1);}
    if(gr){
      if(gr.secret&&R.chance(.6)){const ps=purposeSecret(gr);addSecret({kind:'DEED',about:ids[0],group:gr.id,text:`${gr.name} struck ${t.name} in the ${ord(year)} year: ${names(ids)}.`,keepers:ids,importance:ps?ps.importance:6,eventRef:ev.id},year);L(year,'',`${t.name} never saw who struck`,'meta','secrets',1);}
      else addGrudge(t.id,'group',gr.id,R.int(3,5)+(T(t,'vengeful')?1:0),ev,year);
    }else{const sec=coverUp(ev,atts[0],year);addGrudge(t.id,'god',atts[0].id,sec?R.int(2,3):R.int(3,5)+(T(t,'vengeful')?2:0),ev,year);}
    return ev;
  }
  function seclude(g,year,mot){
    const place=R.pick(SECLUDE),dur=R.int(8,25);
    const ev=newEvent('seclusion',year,[g.id],[],{place,dur});
    g.seclusions.push({start:year,end:year+dur,ev:ev.id});g.secludedUntil=year+dur;
    L(year,'SECLUSION',`${ev.id} ${g.name} withdrew ${place} until ${Y(year+dur)}  [${mot}]`,'event','misc');
  }
  function forgive(g,t,gd,year,mot){
    const ev=newEvent('forgive',year,[g.id],[t.id],{grudge:gd.id});
    if(gd.tt==='god'){gd.resolved=ev.id;L(year,'FORGIVE',`${ev.id} ${g.name} forgave ${t.name}; ${gd.id} ends  [${mot}]`,'grudge','conflict');}
    else{gd.forgiven.add(t.id);L(year,'FORGIVE',`${ev.id} ${g.name} forgave ${t.name}, but not ${gname(gd.target)}; ${gd.id} still stands for the others  [${mot}]`,'grudge','conflict');}
    bump(t.id,g.id,2);
  }
  function accuse(g,t,year,mot){
    const ev=newEvent('accusation',year,[g.id],[t.id]);L(year,'ACCUSES',`${ev.id} ${g.name} accused ${t.name} of plotting against it  [${mot}]`,'event','conflict');
    addGrudge(g.id,'god',t.id,R.int(2,3),ev,year);bump(t.id,g.id,-2);
  }
  function madAct(g,year){
    const friends=alive().filter(o=>o!==g&&friend(g.id,o.id));
    const ks=secrets.filter(s=>!s.exposed&&s.keepers.has(g.id));
    L(year,'MADNESS',`${g.name} acts against its own heart (mad ${T(g,'mad')})`,'death','motive');
    if(friends.length&&R.chance(.5))return strike([g],R.pick(friends),year,'MAD',null);
    if(ks.length&&R.chance(.6))return expose(R.pick(ks),g,year,'MAD',null);
    const o=alive().filter(x=>x!==g);if(o.length)accuse(g,R.pick(o),year,'MAD');
  }

  /* ---- war ---- */
  function startWar(g,t,year,mot,extraA=[],gr=null){
    if(!t.alive||atWar(t.id))return;
    const sideA=[g.id,...extraA.map(x=>x.id).filter(x=>x!==g.id&&!atWar(x))],sideB=[t.id];
    for(const x of alive()){
      if(sideA.includes(x.id)||sideB.includes(x.id)||!active(x,year)||atWar(x.id))continue;
      const forT=Math.max(friend(x.id,t.id),...groupsOf(x.id).filter(q=>q.members.includes(t.id)).map(q=>q.loyalty[x.id]));
      const forG=Math.max(friend(x.id,g.id),...groupsOf(x.id).filter(q=>q.members.includes(g.id)).map(q=>q.loyalty[x.id]));
      if(forT>=6&&forT>forG&&R.chance(.6))sideB.push(x.id);
      else if(forG>=6&&forG>forT&&R.chance(.6))sideA.push(x.id);
    }
    const ev=newEvent('war_start',year,sideA.slice(),sideB.slice(),{group:gr?gr.id:null});
    wars.push({eventId:ev.id,sideA,sideB,start:year,endYear:year+R.int(3,14),over:false});
    L(year,'WAR',`${ev.id} ${gr?gr.name+': ':''}${names(sideA)} against ${names(sideB)}  power ${sumP(sideA.map(i=>gods[i]))} vs ${sumP(sideB.map(i=>gods[i]))}  [${mot}]`,'war','conflict');
    if(gr)addGrudge(t.id,'group',gr.id,5,ev,year);else addGrudge(t.id,'god',g.id,R.int(4,6),ev,year);
  }
  function endWar(w,year){
    w.over=true;w.endYear=year;
    const A=w.sideA.filter(id=>gods[id].alive),B=w.sideB.filter(id=>gods[id].alive);
    if(!A.length||!B.length){L(year,'WAR END',`${w.eventId} ended; one side no longer stands`,'war','conflict');return;}
    const pa=sumP(A.map(i=>gods[i]))*(0.75+R.f()*.5),pb=sumP(B.map(i=>gods[i]))*(0.75+R.f()*.5);
    const diff=Math.abs(pa-pb)/Math.max(pa,pb);
    if(diff<.12){
      const ev=newEvent('war_end',year,A.slice(),B.slice(),{outcome:'stalemate',war:w.eventId});
      L(year,'WAR END',`${ev.id} ${w.eventId} ended in stalemate`,'war','conflict');
      for(const ldr of [A[0],B[0]])if(T(gods[ldr],'proud')&&R.chance(.5))makeLie(ev,ldr,'INFLATE_DEEDS','INVERT_OUTCOME',{outcome:'victory',winner:ldr},null,`the other side's account`,year);
      return;
    }
    const win=pa>pb?A:B,lose=pa>pb?B:A,lw=gods[win[0]],ll=gods[lose[0]];
    const ev=newEvent('war_end',year,win.slice(),lose.slice(),{outcome:'victory',war:w.eventId,decisive:diff>.4});
    const dw=diff>.4?2:1;lw.power+=dw;ll.power=Math.max(1,ll.power-dw);
    L(year,'WAR END',`${ev.id} ${names(win)} prevailed over ${names(lose)}${diff>.4?', a rout':''}  (power ${lw.name} +${dw}, ${ll.name} −${dw})`,'war','conflict');
    const gd=grudgeVs(lw.id,ll.id);if(gd&&diff>.4&&gd.tt==='god'){gd.resolved=ev.id;L(year,'',`${gd.id} satisfied by victory`,'grudge','conflict',1);}
    addGrudge(ll.id,'god',lw.id,R.int(4,6)+(T(ll,'vengeful')?1:0),ev,year);
    if(R.chance(.25+(T(ll,'proud')?.3:0)))makeLie(ev,ll.id,'COVER_UP','INVERT_OUTCOME',{outcome:'stalemate'},null,`${poss(lw.name)} account, and ${poss(ll.name)} lost power`,year);
    if(win.length>1&&T(lw,'proud')&&R.chance(.6))makeLie(ev,lw.id,'INFLATE_DEEDS','OMIT_ALLIES',{allies:[]},null,`${names(win.slice(1))} remember fighting beside them`,year);
  }

  /* ---- killing ---- */
  function attemptKill(atts,t,year,mot,gr){
    if(!t.alive||!atts.length)return;
    const ids=atts.map(a=>a.id),pa=sumP(atts),pt=t.power;
    const p=pa>pt?Math.min(.2,.015+.22*(pa-pt)/pa):.005;
    const roll=R.f();const who=gr?`${gr.name} (${names(ids)})`:atts[0].name;
    if(roll>=p){
      const ev=newEvent('kill_attempt',year,ids,[t.id],{group:gr?gr.id:null});
      L(year,'KILL FAILED',`${ev.id} ${who} tried to kill ${t.name} and failed  power ${pa} vs ${pt}, p=${p.toFixed(2)}  [${mot}]`,'war','conflict');
      if(gr&&gr.secret&&R.chance(.5)){addSecret({kind:'DEED',about:ids[0],group:gr.id,text:`${gr.name} tried to kill ${t.name}: ${names(ids)}.`,keepers:ids,importance:9,eventRef:ev.id},year);L(year,'',`${t.name} survived without learning who struck`,'meta','secrets',1);}
      else if(gr)addGrudge(t.id,'group',gr.id,8,ev,year);
      else{const sec=coverUp(ev,atts[0],year);addGrudge(t.id,'god',atts[0].id,sec?4:8,ev,year);}
      return;
    }
    const ev=newEvent('deicide',year,ids,[t.id],{group:gr?gr.id:null,victimPower:pt});
    const friendsOfT=alive().filter(o=>o!==t&&!ids.includes(o.id)&&(friend(o.id,t.id)>=4||o.creator===t.id||t.creator===o.id));
    t.alive=false;t.deathYear=year;t.killers=ids.slice();S.deicides.push(ev);
    L(year,'DEICIDE',`${ev.id} ${who} slew ${t.name}  power ${pa} vs ${pt}, roll ${roll.toFixed(3)} < ${p.toFixed(2)}  [${mot}]`,'death','conflict');
    L(year,'',`orphaned likes: ${t.likes.map(lbl).join(', ')}`,'death','conflict',1);
    const gain=Math.floor(pt/2);if(gain){atts[0].power+=gain;L(year,'',`power: ${atts[0].name} +${gain} (half of ${poss(t.name)} ${pt})`,'meta','conflict',1);}
    const hidden=(gr&&gr.secret&&R.chance(.6))||(!gr&&T(atts[0],'secretive')&&R.chance(.6));
    let sec=null;
    if(hidden){
      sec=addSecret({kind:'DEED',about:ids[0],group:gr?gr.id:null,text:`${names(ids)} slew ${t.name} in the ${ord(year)} year.`,keepers:ids,importance:10,eventRef:ev.id},year);
      makeLie(ev,ids[0],'KEEP_SECRET','OMIT_ACTOR',{actor:null},null,`the corpse of ${t.name} remembers its killers`,year,{tellers:ids,group:gr?gr.id:null});
      L(year,'','no one saw the killers; the heavens do not know who struck','meta','secrets',1);
    }else{
      for(const f of friendsOfT)addGrudge(f.id,gr?'group':'god',gr?gr.id:atts[0].id,R.int(5,8),ev,year);
      if(ids.length>1&&T(atts[0],'proud')&&R.chance(.7))makeLie(ev,ids[0],'INFLATE_DEEDS','OMIT_ALLIES',{allies:[]},null,`${names(ids.slice(1))} were there`,year);
    }
    for(const g of grudges)if(!g.resolved&&(g.holder===t.id||(g.tt==='god'&&g.target===t.id)))g.resolved=ev.id;
    for(const q of groups){if(q.dissolved)continue;
      if(q.members.includes(t.id))leaveGroup(q,t.id,year);
      if(q.dissolved)continue;
      if(q.principle.target===t.id){
        if(q.principle.kind==='AVENGE'){const nx=(q.principle.killers||[]).find(k=>gods[k].alive&&k!==t.id);
          if(nx!=null){q.principle.target=nx;L(year,'',`${q.name} turns on ${nm(nx)}`,'ally','groups',1);}else dissolve(q,year,'its purpose is fulfilled');}
        else dissolve(q,year,'its purpose is fulfilled');}
      else if(q.principle.protect===t.id)dissolve(q,year,`${t.name}, whom it protected, is dead`);}
    {const mourners=friendsOfT.filter(f=>f.alive).sort((a,b)=>friend(b.id,t.id)-friend(a.id,t.id)).slice(0,4);
     if(mourners.length>=2&&R.chance(.7)){const known=!sec;
       foundGroup(mourners,{kind:'AVENGE',dead:t.id,killers:ids.slice(),known,target:known?(ids.find(i=>gods[i].alive)??null):null,secretId:sec?sec.id:null},year,'grief for '+t.name);}}
    for(const w of wars)if(!w.over&&(w.sideA.includes(t.id)||w.sideB.includes(t.id))){w.over=true;w.endYear=year;L(year,'',`${w.eventId} ends with ${poss(t.name)} death`,'war','conflict',1);}
    if(R.chance(.65))pendingCorpses.push({dead:t.id,year:year+R.int(5,30),power:pt,killers:ids,secret:sec});
  }

  /* ---- groups ---- */
  function alignScore(g,p){
    switch(p.kind){
      case 'OPPOSE':case 'KILL':return p.target===g.id?0:grudgeScore(g.id,p.target);
      case 'AVENGE':return Math.max(friend(g.id,p.dead),kin(g.id,p.dead)?6:0,p.target!=null?grudgeScore(g.id,p.target):0);
      case 'GUARD':{const s=secrets.find(x=>x.id===p.secretId);return s&&!s.exposed?(s.keepers.has(g.id)?importanceFor(g,s):(s.knowers.has(g.id)?5:0)):0;}
      case 'PROTECT':return Math.max(friend(g.id,p.protect),kin(g.id,p.protect)?6:0);
      case 'RECORD':return T(g,'historian');
      case 'VEIL':return Math.max(T(g,'obfuscator'),T(g,'secretive'));
    }return 0;
  }
  function principleOpp(g,p){
    const o=[];
    if(p.target!=null&&gods[p.target].alive){if(p.target===g.id)o.push({type:'SELF',score:Infinity});else o.push(...oppHarm(g,gods[p.target]));}
    if(p.kind==='VEIL')o.push(...historianOpp(g));
    if(p.kind==='GUARD')o.push(...historianOpp(g));
    if(T(g,'reclusive'))o.push({type:'TRAIT:reclusive',score:T(g,'reclusive')});
    return o;
  }
  const topOf=list=>list.filter(o=>o&&o.score>0).sort((a,b)=>b.score-a.score)[0]||null;
  function groupName(p){
    let n,tries=0;
    do{n=`the ${R.pick(G_ADJ)} ${R.pick(G_NOUN)}`;tries++;}while(usedGroupNames.has(n)&&tries<30);
    usedGroupNames.add(n);return n;
  }
  function chooseLeader(gr,init){
    const ms=gr.members.map(m=>gods[m]).sort((a,b)=>b.power-a.power);
    if(init&&(T(init,'proud')||T(init,'ambitious')))return init.id;
    if(ms[1]&&ms[0].power>=1.5*ms[1].power)return ms[0].id;
    return null;
  }
  const initLoyalty=(g,founder)=>clamp(5+Math.round(T(g,'loyal')/2)-Math.round(T(g,'opportunist')/2)+(founder?1:0),1,10);
  function membershipSecret(gr,m,year){
    return addSecret({kind:'MEMBERSHIP',about:m,group:gr.id,text:`${nm(m)} is a member of ${gr.name}.`,keepers:[...new Set([m,...gr.members])],importance:R.int(5,8)},year,1,true);
  }
  function foundGroup(founders,p,year,mot){
    const init=founders[0];const members=[init];
    for(const f of founders.slice(1)){
      if(!f.alive||members.includes(f))continue;
      const sup=Math.max(alignScore(f,p),friend(f.id,init.id));const o=topOf(principleOpp(f,p));
      if(!o||(o.score!==Infinity&&(sup>o.score||(sup===o.score&&R.chance(.5)))))members.push(f);
      else L(year,'DECLINES',`${f.name} declined ${poss(init.name)} offer to band together ${principlePhrase(p,S)} (${sup} vs ${mlabel(o)})`,'meta','groups');
    }
    if(members.length<2)return null;
    const secret=p.kind==='KILL'||p.kind==='VEIL'||p.kind==='GUARD'||(p.kind!=='RECORD'&&members.some(m=>T(m,'secretive')));
    const gr={id:groups.length,name:groupName(p),principle:p,secret,leader:null,members:members.map(m=>m.id),founded:year,dissolved:false,dissolvedWhy:null,hall:false,loyalty:{},parent:null};
    for(const m of members)gr.loyalty[m.id]=initLoyalty(m,m===init);
    gr.leader=chooseLeader(gr,init);
    groups.push(gr);
    for(const a of gr.members)for(const b of gr.members)if(a!==b)bump(a,b,2);
    if(p.kind==='GUARD'){const gs=secrets.find(x=>x.id===p.secretId);if(gs)for(const m of gr.members)gs.keepers.add(m);}
    const ev=newEvent('group_found',year,gr.members.slice(),p.target!=null?[p.target]:[],{group:gr.id,leader:gr.leader});
    L(year,'FOUNDED',`${ev.id} ${names(gr.members)} founded ${gr.name}, sworn ${principlePhrase(p,S)}  [${mot}]`,'ally','groups');
    L(year,'',`${secret?'secret':'public'}; ${gr.leader!=null?'led by '+nm(gr.leader):'leaderless, decides by vote'}; loyalty ${members.map(m=>m.name+' '+gr.loyalty[m.id]).join(', ')}`,'meta','groups',1);
    if(secret){
      const s=addSecret({kind:'GROUP_PURPOSE',about:gr.id,group:gr.id,text:`${gr.name} is a group sworn ${principlePhrase(p,S)}.`,keepers:gr.members,importance:p.kind==='KILL'?9:7,eventRef:ev.id},year);
      for(const m of gr.members)membershipSecret(gr,m,year);
      L(year,'',`each member's membership is kept as its own secret`,'lie','secrets',1);
    }
    return gr;
  }
  function newLeader(gr,year){
    const ms=gr.members.map(m=>gods[m]).sort((a,b)=>b.power-a.power);
    const cand=ms.find(m=>T(m,'proud')||T(m,'ambitious'));
    gr.leader=cand?cand.id:null;
    L(year,'',`${gr.name} ${gr.leader!=null?'now follows '+nm(gr.leader):'now decides by vote'}`,'meta','groups',1);
  }
  function leaveGroup(gr,id,year){
    gr.members=gr.members.filter(m=>m!==id);
    if(gr.leader===id&&gr.members.length)newLeader(gr,year);
    if(gr.members.length<2)dissolve(gr,year,'too few remain');
  }
  function desert(g,gr,year,mot){
    const ev=newEvent('defect',year,[g.id],gr.members.filter(m=>m!==g.id),{group:gr.id});ev.secretRef=gSecretRef(gr);
    L(year,'DEFECT',`${ev.id} ${g.name} walked away from ${gr.name}  [${mot}]`,'ally','groups');
    for(const m of gr.members)bump(m,g.id,-2);leaveGroup(gr,g.id,year);
  }
  function dissolve(gr,year,why){if(gr.dissolved)return;gr.dissolved=true;gr.dissolvedWhy=why;gr.dissolvedYear=year;L(year,'DISSOLVED',`${gr.name} is no more: ${why}`,'ally','groups',1);}
  const gSecretRef=gr=>{if(!gr.secret)return null;const s=purposeSecret(gr);return s&&!s.exposed?s.id:null;};

  function invite(gr,c,year,bonus=0){
    const p=gr.principle;
    const sup=Math.max(alignScore(c,p),bonus,...gr.members.map(m=>friend(c.id,m)));
    const opps=[...principleOpp(c,p),...gr.members.map(m=>{const s=grudgeScore(c.id,m);return s?{type:'GRUDGE',score:s}:null;})];
    const gg=groupGrudge(c.id,gr.id);if(gg)opps.push({type:'GRUDGE',score:gg.sev});
    const o=topOf(opps);
    const ok=!o||(o.score!==Infinity&&(sup>o.score||(sup===o.score&&R.chance(.5))));
    if(!ok){L(year,'',`${c.name} refuses the invitation (${sup} vs ${mlabel(o)})`,'meta','groups',1);return;}
    gr.members.push(c.id);gr.loyalty[c.id]=initLoyalty(c,false);
    if(p.kind==='GUARD'){const gs=secrets.find(x=>x.id===p.secretId);if(gs)gs.keepers.add(c.id);}
    const ev=newEvent('group_join',year,[c.id],[],{group:gr.id});
    L(year,'',`${c.name} joins ${gr.name} (loyalty ${gr.loyalty[c.id]})`,'ally','groups',1);
    if(gr.secret){const ps=purposeSecret(gr);if(ps)ps.keepers.add(c.id);const ms=membershipSecret(gr,c.id,year);ev.secretRef=ms.id;for(const s of secrets)if(s.kind==='MEMBERSHIP'&&s.group===gr.id)s.keepers.add(c.id);}
  }
  function expel(gr,x,year){
    leaveGroup(gr,x.id,year);
    const ev=newEvent('group_expel',year,gr.members.slice(),[x.id],{group:gr.id});ev.secretRef=gSecretRef(gr);
    L(year,'',`${x.name} is cast out of ${gr.name}`,'ally','groups',1);
    if(!gr.dissolved)addGrudge(x.id,'group',gr.id,R.int(4,6),ev,year,2);
  }
  function buildHall(gr,year){
    gr.hall=true;const ev=newEvent('hall_built',year,gr.members.slice(),[],{group:gr.id});ev.secretRef=gSecretRef(gr);
    L(year,'',`${gr.name} raises a hall in the Altus${gr.secret?', hidden from all others':''}`,'ally','groups',1);
    if(gr.secret)addSecret({kind:'LOCATION',about:gr.id,group:gr.id,text:`The hall of ${gr.name} lies hidden in the Altus.`,keepers:gr.members,importance:6,eventRef:ev.id},year,2);
  }

  function council(gr,year,proposer,drive){
    if(gr.dissolved)return;
    gr.members=gr.members.filter(m=>gods[m].alive);if(gr.members.length<2)return dissolve(gr,year,'too few remain');
    const prop=proposer&&gr.members.includes(proposer.id)?proposer:gods[R.pick(gr.members)];
    const P=genProposal(gr,prop,year);if(!P)return;
    const vote=m=>{const sup=Math.max(gr.loyalty[m.id],P.support?P.support(m):0);const o=topOf(P.opp(m));
      if(!o)return {yes:true,why:'no objection'};
      const yes=o.score!==Infinity&&(sup>o.score||(sup===o.score&&R.chance(.5)));return {yes,why:`${sup} vs ${mlabel(o)}`};};
    let passed,txt;
    const ld=gr.leader!=null&&gr.members.includes(gr.leader)?gods[gr.leader]:null;
    if(ld){const v=ld===prop?{yes:true,why:'its own proposal'}:vote(ld);passed=v.yes;txt=`${ld.name} ${passed?'approves':'rejects'} (${v.why})`;}
    else{let y=0,n=0;for(const m of gr.members){if(m===prop.id){y++;continue;}vote(gods[m]).yes?y++:n++;}passed=y>n||(y===n&&R.chance(.5));txt=`vote ${y}–${n}${y===n?', tie broken by chance':''}: ${passed?'carried':'rejected'}`;}
    L(year,'COUNCIL',`${gr.name}: ${prop.name} proposes to ${P.label}${drive?` [${mlabel(drive)}]`:''}. ${txt}`,'ally','groups');
    if(!passed)return;
    const parts=[prop];
    for(const m of gr.members){
      if(m===prop.id||(P.exclude&&P.exclude.includes(m)))continue;
      const g=gods[m],o=topOf(P.opp(g));
      if(!o){parts.push(g);continue;}
      const lo=gr.loyalty[m];
      if(o.score===Infinity||o.score>lo||(o.score===lo&&R.chance(.5))){
        for(const x of gr.members)if(x!==m)bump(x,m,-2);gr.loyalty[m]=Math.max(1,lo-1);
        L(year,'REFUSES',`${g.name} refuses to take part (${mlabel(o)} > LOYALTY ${lo}); its standing in ${gr.name} falls`,'meta','groups',1);
        if(o.score!==Infinity)addGrudge(m,'group',gr.id,Math.max(1,Math.ceil(o.score/3)),null,year,2);
      }else{
        gr.loyalty[m]=Math.max(1,lo-1);parts.push(g);
        L(year,'COMPLIES',`${g.name} takes part against its will (LOYALTY ${lo} ≥ ${mlabel(o)})`,'meta','groups',1);
        addGrudge(m,'group',gr.id,Math.min(10,o.score),null,year,2);
      }
    }
    P.run(parts);
  }
  function genProposal(gr,prop,year){
    const p=gr.principle,C=[],memb=gr.members.map(m=>gods[m]);
    const tgt=p.target!=null?gods[p.target]:null;
    const by=`council of ${gr.name}`;
    if(tgt&&tgt.alive){
      const hOpp=m=>m.id===tgt.id?[{type:'SELF',score:Infinity}]:oppHarm(m,tgt);
      const sup=m=>grudgeScore(m.id,tgt.id);
      C.push({w:2,label:`strike ${tgt.name}`,opp:hOpp,support:sup,run:ps=>strike(ps,tgt,year,by,gr)});
      if(!atWar(tgt.id))C.push({w:.6,label:`make war on ${tgt.name}`,opp:hOpp,support:sup,run:ps=>startWar(ps[0],tgt,year,by,ps.slice(1),gr)});
      C.push({w:1,label:`agree on a lie about ${tgt.name}`,opp:m=>[...hOpp(m),...historianOpp(m)],support:sup,run:ps=>smear(ps[0],tgt,year,by,gr,ps)});
      if(p.kind==='KILL'||p.kind==='AVENGE'){const pa=sumP(memb);C.push({w:pa>tgt.power?1.1:.15,label:`kill ${tgt.name}`,opp:hOpp,support:sup,run:ps=>attemptKill(ps,tgt,year,by,gr)});}
    }
    if(p.kind==='AVENGE'&&!p.known){const s=secrets.find(x=>x.id===p.secretId);if(s&&!s.exposed)C.push({w:3,label:`hunt the slayers of ${nm(p.dead)} (${s.id})`,opp:()=>[],support:m=>Math.max(friend(m.id,p.dead),6),run:ps=>investigate(ps,s,year,by,gr)});}
    if(p.kind==='GUARD'){
      const s=secrets.find(x=>x.id===p.secretId);
      if(s&&!s.exposed){
        const th=[...s.threats].filter(x=>gods[x].alive&&!gr.members.includes(x));
        if(th.length){const x=gods[R.pick(th)];C.push({w:2.5,label:`silence ${x.name}, who has sought the secret`,opp:m=>oppHarm(m,x),support:m=>importanceFor(m,s),run:ps=>strike(ps,x,year,by,gr)});}
        const ev=s.eventRef?events.find(e=>e.id===s.eventRef):null;
        if(ev){const un=memb.filter(m=>!story[ev.id+'|'+m.id]&&!T(m,'historian'));
          if(un.length>=2)C.push({w:1.8,label:`agree on one story about ${ev.id}`,opp:m=>historianOpp(m),support:m=>importanceFor(m,s),run:ps=>{
            const ts=ps.filter(x=>!story[ev.id+'|'+x.id]&&!T(x,'historian')).map(x=>x.id);if(ts.length<2)return;
            if(ev.type==='location')makeLie(ev,ts[0],'GUARD_SECRET','FABRICATE_LOCATION',{place:R.pick(WOODS_PLACES)},null,`the sanctum's holder gives the true door`,year,{tellers:ts,group:gr.id});
            else makeLie(ev,ts[0],'GUARD_SECRET','OMIT_ACTOR',{actor:null},null,`those who were there remember`,year,{tellers:ts,group:gr.id});}});}
      }
    }
    if(p.kind==='PROTECT'){
      const pr=gods[p.protect];
      if(pr&&pr.alive){const ag=aggressorsOf(pr.id,year).filter(a=>!gr.members.includes(a.id));
        if(ag.length){const a=ag[0];
          C.push({w:2.4,label:`strike ${a.name}, who harmed ${pr.name}`,opp:m=>oppHarm(m,a),support:m=>friend(m.id,pr.id),run:ps=>strike(ps,a,year,by,gr)});
          if(!atWar(a.id)&&sumP(memb)>=a.power)C.push({w:.8,label:`make war on ${a.name}`,opp:m=>oppHarm(m,a),support:m=>friend(m.id,pr.id),run:ps=>startWar(ps[0],a,year,by,ps.slice(1),gr)});}}
    }
    if(p.kind==='RECORD'){
      const sec=secrets.filter(s=>!s.exposed&&!gr.members.some(m=>s.keepers.has(m)));
      if(sec.length){const s=R.pick(sec);C.push({w:2,label:`uncover a hidden thing (${s.id})`,opp:()=>[],run:ps=>investigate(ps,s,year,by,gr)});}
      const ls=lies.filter(l=>!l.exposed&&!l.tellers.some(t=>gr.members.includes(t)));
      if(ls.length){const l=R.pick(ls);C.push({w:2,label:`expose ${poss(nm(l.teller))} lie (${l.id})`,opp:()=>[],run:ps=>correct(ps,l,year,by,gr)});}
    }
    if(p.kind==='VEIL')C.push({w:2,label:'agree on a false account to spread',opp:m=>historianOpp(m),run:ps=>obfuscate(ps[0],year,by,gr,ps)});
    const cands=alive().filter(o=>!gr.members.includes(o.id)&&o.id!==p.target&&alignScore(o,p)>=3&&!grudgeVs(o.id,prop.id));
    if(cands.length&&gr.members.length<6){const c=R.pick(cands);C.push({w:1.2,label:`invite ${c.name}`,opp:m=>{const s=grudgeScore(m.id,c.id);return s?[{type:'GRUDGE',score:s}]:[];},support:m=>friend(m.id,c.id),run:()=>invite(gr,c,year)});}
    const bad=memb.filter(m=>m!==prop&&((groupGrudge(m.id,gr.id)||{sev:0}).sev>=3||gr.members.filter(x=>x!==m.id).reduce((s,x)=>s+getOp(x,m.id),0)/(gr.members.length-1)<-1));
    if(bad.length){const x=R.pick(bad);C.push({w:2,label:`cast out ${x.name}`,exclude:[x.id],opp:m=>m===x?[{type:'SELF',score:Infinity}]:(friend(m.id,x.id)?[{type:'FRIENDSHIP',score:friend(m.id,x.id)}]:[]),run:()=>expel(gr,x,year)});}
    if(!gr.hall&&gr.members.length>=3)C.push({w:.7,label:'raise a hall in the Altus',opp:()=>[],run:()=>buildHall(gr,year)});
    if(gr.leader==null&&(T(prop,'proud')||T(prop,'ambitious')))C.push({w:.6,label:`make ${prop.name} its leader`,opp:m=>m===prop?[]:[grudgeScore(m.id,prop.id)?{type:'GRUDGE',score:grudgeScore(m.id,prop.id)}:null,T(m,'proud')?{type:'TRAIT:proud',score:T(m,'proud')}:null],run:()=>{gr.leader=prop.id;L(year,'',`${prop.name} now leads ${gr.name}`,'ally','groups',1);}});
    if(!C.length)return null;
    return R.weighted(C);
  }

  /* ---- internal drama ---- */
  function internalDrama(g,gr,d,year){
    const symp=gr.members.filter(m=>m!==g.id&&(getOp(m,g.id)>=3||groupGrudge(m,gr.id)));
    const keptSecrets=secrets.filter(s=>!s.exposed&&s.group===gr.id);
    const opts=[{w:1,k:'defect'}];
    if(symp.length)opts.push({w:1.3,k:'splinter'});
    if(keptSecrets.length||!gr.secret)opts.push({w:1,k:'betray'});
    const k=R.weighted(opts).k;
    if(!decide(g,d,[{type:'LOYALTY',score:gr.loyalty[g.id]}],year,`${k} ${gr.name}`))return;
    const mot=mlabel(d)+` vs ${gr.name}`;
    if(k==='defect'){desert(g,gr,year,mot);return;}
    if(k==='betray')return betrayGroup(g,gr,year,mot);
    const followers=symp.map(m=>gods[m]);
    const ev=newEvent('splinter',year,[g.id,...symp],[],{group:gr.id});ev.secretRef=gSecretRef(gr);
    L(year,'SPLINTER',`${ev.id} ${names([g.id,...symp])} broke away from ${gr.name}  [${mot}]`,'ally','groups');
    for(const m of [g.id,...symp])leaveGroup(gr,m,year);
    const ng=foundGroup([g,...followers],{...gr.principle},year,`splinter of ${gr.name}`);
    if(ng){ng.parent=gr.id;ev.data.newGroup=ng.id;if(!gr.dissolved)for(const m of gr.members)addGrudge(m,'group',ng.id,R.int(3,5),ev,year);}
  }
  function betrayGroup(g,gr,year,mot){
    const ks=secrets.filter(s=>!s.exposed&&s.group===gr.id&&s.about!==g.id);
    const ev=newEvent('betrayal',year,[g.id],gr.members.filter(m=>m!==g.id),{group:gr.id,leaked:!!ks.length});
    L(year,'BETRAYAL',`${ev.id} ${g.name} betrayed ${gr.name}  [${mot}]`,'war','groups');
    const others=gr.members.filter(m=>m!==g.id);
    leaveGroup(gr,g.id,year);
    for(const m of others)addGrudge(m,'god',g.id,R.int(5,8),ev,year);
    if(ks.length){const s=ks.find(x=>x.kind==='GROUP_PURPOSE')||R.pick(ks);expose(s,g,year,'betrayal',null);}
    else{const victims=others.filter(m=>gods[m].alive);if(victims.length)strike([g],gods[R.pick(victims)],year,'betrayal',null);}
  }

  /* ---- knowledge acts ---- */
  function expose(s,ex,year,mot,gr){
    if(s.exposed)return;
    s.exposed=true;s.exposer=ex.id;
    const ev=newEvent('exposure',year,gr?gr.members.filter(m=>gods[m].alive):[ex.id],[...s.keepers].filter(k=>gods[k].alive&&k!==ex.id),{secret:s.id,group:gr?gr.id:null});
    L(year,'EXPOSED',`${ev.id} ${gr?gr.name:ex.name} laid bare ${s.id}: “${s.text}”  [${mot}]`,'lie','secrets');
    for(const q of groups){if(q.dissolved)continue;
      if(q.principle.kind==='GUARD'&&q.principle.secretId===s.id)dissolve(q,year,'its secret is out');
      else if(q.principle.kind==='AVENGE'&&!q.principle.known&&q.principle.secretId===s.id){
        const e=events.find(x=>x.id===s.eventRef);const ks=e?e.actors.filter(k=>gods[k].alive):[];
        q.principle.known=true;q.principle.killers=e?e.actors.slice():[];q.principle.target=ks.length?ks[0]:null;
        L(year,'',`${q.name} learns who slew ${nm(q.principle.dead)}: ${names(q.principle.killers)}`,'ally','groups',1);
        for(const m of q.members)for(const k of ks)addGrudge(m,'god',k,7,ev,year,2);
        if(!ks.length)dissolve(q,year,'its quarry is already dead');}}
    for(const k of s.keepers)if(k!==ex.id&&gods[k].alive&&!(gr&&gr.members.includes(k)))addGrudge(k,gr?'group':'god',gr?gr.id:ex.id,Math.ceil(s.importance/2)+1,ev,year);
    if(s.kind==='GROUP_PURPOSE'){const q=groups[s.group];if(q.principle.target!=null&&gods[q.principle.target].alive&&!q.dissolved)addGrudge(q.principle.target,'group',q.id,q.principle.kind==='KILL'?8:6,ev,year);}
    if(s.kind==='MEMBERSHIP'){const q=groups[s.group];const t=q.principle.target;if(t!=null&&gods[t].alive&&t!==s.about)addGrudge(t,'god',s.about,4,ev,year);}
    if(s.kind==='DEED'&&s.eventRef){const e=events.find(x=>x.id===s.eventRef);
      if(e.type==='deicide'){const v=e.targets[0];for(const o of alive())if((friend(o.id,v)>=4||o.creator===v||o.sourceCorpse===v)&&!e.actors.includes(o.id))for(const a of e.actors)addGrudge(o.id,'god',a,6,ev,year);}
      else for(const v of e.targets)if(gods[v].alive)for(const a of e.actors)addGrudge(v,'god',a,5,ev,year);}
  }
  function investigate(ps,s,year,mot,gr){
    const kp=sumP([...s.keepers].map(k=>gods[k]).filter(k=>k.alive)),pa=sumP(ps);
    const p=kp?pa/(pa+kp):1;
    if(R.f()<p)return expose(s,ps[0],year,`${mot}, power ${pa} vs ${kp}`,gr);
    for(const x of ps)s.threats.add(x.id);
    L(year,'FOILED',`${gr?gr.name:ps[0].name} sought ${s.id} and failed (power ${pa} vs ${kp}); its keepers have noticed  [${mot}]`,'meta','secrets');
  }
  function correct(ps,l,year,mot,gr){
    const t=gods[l.teller];const pa=sumP(ps),pt=t.alive?t.power:0;
    if(R.f()>=(pt?pa/(pa+pt):.9)){L(year,'FOILED',`${gr?gr.name:ps[0].name} tried to disprove ${l.id} and failed  [${mot}]`,'meta','secrets');return;}
    l.exposed=true;
    const ev=newEvent('correction',year,ps.map(p=>p.id),[l.teller],{lie:l.id,group:gr?gr.id:null});
    L(year,'CORRECTED',`${ev.id} ${gr?gr.name:ps[0].name} proved ${poss(nm(l.teller))} story false (${l.id})  [${mot}]`,'lie','secrets');
    for(const x of l.tellers)if(gods[x].alive)addGrudge(x,gr?'group':'god',gr?gr.id:ps[0].id,R.int(3,5),ev,year);
    if(l.targetGod!=null&&gods[l.targetGod].alive)for(const x of l.tellers)if(gods[x].alive)addGrudge(l.targetGod,'god',x,R.int(3,6),ev,year);
  }
  function smear(g,t,year,mot,gr,ps){
    const harmful=events.filter(e=>['offense','strike','kill_attempt','insult','betrayal'].includes(e.type)&&!e.actors.includes(t.id)&&!e.targets.includes(t.id)&&!e.targets.includes(g.id)&&!e.secretRef&&e.year>=t.born);
    if(!harmful.length)return;
    const withAlibi=harmful.filter(e=>presence(t.id,e.year,e.id));
    const ev=R.pick(withAlibi.length?withAlibi:harmful);const al=presence(t.id,ev.year,ev.id);
    const lie=makeLie(ev,g.id,gr?'GROUP_SMEAR':'SMEAR_RIVAL','SWAP_ACTOR',{actor:t.id},t.id,al?`${t.name} was ${al.desc}`:`${names(ev.targets)} saw who truly did it`,year,gr?{tellers:ps.map(p=>p.id),group:gr.id,indent:1}:{indent:0});
    if(!lie)return;
    for(const v of ev.targets)if(gods[v].alive&&!T(gods[v],'historian')){bump(v,t.id,-2);if(R.chance(.35)){L(year,'',`${nm(v)} believes it`,'lie','secrets',2);addGrudge(v,'god',t.id,3,ev,year,2);}}
  }
  function obfuscate(g,year,mot,gr,ps){
    const pool=events.filter(e=>['offense','strike','war_end','deicide','creation','corpse_birth','group_found','kill_attempt'].includes(e.type)&&!e.secretRef);
    if(!pool.length)return;
    const ev=R.pick(pool);let lie;
    const opt=gr?{tellers:ps.map(p=>p.id),group:gr.id,indent:1}:{indent:0};
    if(ev.type==='war_end')lie=makeLie(ev,g.id,'OBFUSCATE','INVERT_OUTCOME',ev.data.outcome==='stalemate'?{outcome:'victory',winner:ev.actors[0]}:{outcome:'stalemate'},null,'any other account of the war',year,opt);
    else{const others=alive().filter(o=>!ev.actors.includes(o.id)&&!ev.targets.includes(o.id)&&o.born<=ev.year);
      if(others.length&&!['creation','corpse_birth','group_found'].includes(ev.type)){const o=R.pick(others);lie=makeLie(ev,g.id,'OBFUSCATE','SWAP_ACTOR',{actor:o.id},o.id,`any other account names ${names(ev.actors)}`,year,opt);}
      else lie=makeLie(ev,g.id,'OBFUSCATE','SHIFT_DATE',{year:Math.max(1,ev.year+R.int(-40,40))},null,`the true year is ${ev.year}, as other accounts show`,year,opt);}
    return lie;
  }
  function sowDiscord(g,t,year,mot){
    const friends=alive().filter(b=>b!==g&&b!==t&&getOp(b.id,t.id)>=2);
    for(const b of R.shuffle(friends)){
      const evs=events.filter(e=>['offense','strike','kill_attempt'].includes(e.type)&&e.targets.includes(b.id)&&!e.actors.includes(t.id)&&!e.actors.includes(g.id)&&!e.secretRef&&e.year>=t.born&&!story[e.id+'|'+g.id]);
      if(!evs.length)continue;
      const ev=R.pick(evs),al=presence(t.id,ev.year,ev.id);
      const lie=makeLie(ev,g.id,'SOW_DISCORD','SWAP_ACTOR',{actor:t.id},t.id,al?`${t.name} was ${al.desc}`:`${poss(b.name)} own memory of who did it`,year,{indent:0});
      if(lie){bump(b.id,t.id,-3);L(year,'',`${b.name} hears it; ${b.name}→${t.name} −3  [${mot}]`,'lie','secrets',2);if(R.chance(.5))addGrudge(b.id,'god',t.id,R.int(2,4),ev,year,2);return;}
    }
  }

  /* ---- motivation-driven acts ---- */
  function actGrudge(g,d,year){
    const gd=d.grudge;
    if(gd.tt==='group'){
      const gr=groups[gd.target];
      if(gr.members.includes(g.id))return internalDrama(g,gr,d,year);
      const ks=secrets.filter(s=>!s.exposed&&s.group===gr.id&&(s.keepers.has(g.id)||s.knowers.has(g.id)));
      if(ks.length&&R.chance(.5)){const s=R.pick(ks);if(decide(g,d,[{type:'SECRET',score:s.keepers.has(g.id)?importanceFor(g,s):0}],year,`reveal ${s.id}`))return expose(s,g,year,mlabel(d)+` vs ${gr.name}`,null);return;}
      const c=gr.members.filter(m=>gods[m].alive&&!gd.forgiven.has(m)&&m!==g.id);if(!c.length)return;
      return harm(g,gods[R.pick(c)],d,year);
    }
    const kn=secrets.filter(s=>!s.exposed&&s.knowers.has(g.id)&&s.keepers.has(gd.target)&&!s.keepers.has(g.id));
    if(kn.length&&R.chance(.35)){const s=R.pick(kn);if(!decide(g,d,oppHarm(g,gods[gd.target]),year,`reveal ${s.id}`))return;return expose(s,g,year,`${mlabel(d)} vs ${nm(gd.target)}, using what it was told`,null);}
    return harm(g,gods[gd.target],d,year);
  }
  function harm(g,t,d,year){
    if(!t.alive||t===g)return;
    const sev=d.score,opts=[];
    if(sev>=9&&(T(g,'devourer')||T(g,'vengeful')))opts.push({w:.8,k:'kill'});
    if(sev>=6&&!atWar(g.id)&&!atWar(t.id))opts.push({w:1,k:'war'});
    const allies=alive().filter(o=>o!==g&&o!==t&&grudgeScore(o.id,t.id)>=3&&grudgeScore(o.id,t.id)>friend(o.id,t.id));
    if(allies.length&&sev>=4)opts.push({w:1.2,k:'plot'});
    if(g.power>=10&&t.power>=g.power*.5&&sev>=6)opts.push({w:.6,k:'champion'});
    opts.push({w:.7,k:'smear'});
    const friendsT=alive().filter(b=>b!==g&&b!==t&&getOp(b.id,t.id)>=2);
    if(friendsT.length&&sev>=3&&g.power<=t.power*1.2)opts.push({w:1.2,k:'sow'});
    if(sev>=3&&conflictItems(g,t).length)opts.push({w:1.5,k:'offense'});
    if(sev>=4)opts.push({w:2,k:'strike'});
    const k=R.weighted(opts).k;
    const what={kill:`kill ${t.name}`,war:`make war on ${t.name}`,plot:`plot against ${t.name}`,champion:`make a champion against ${t.name}`,smear:`spread lies about ${t.name}`,sow:`turn ${poss(t.name)} friends against it`,offense:`offend ${t.name}`,strike:`strike ${t.name}`}[k];
    if(!decide(g,d,oppHarm(g,t),year,what))return;
    const mot=`${mlabel(d)} vs ${t.name}`;
    switch(k){
      case 'kill':return attemptKill([g],t,year,mot,null);
      case 'war':return startWar(g,t,year,mot);
      case 'plot':return foundGroup([g,...R.shuffle(allies).slice(0,R.int(1,3))],{kind:(sev>=8||T(g,'devourer'))?'KILL':'OPPOSE',target:t.id},year,mot);
      case 'champion':return createGod(g,'champion',year,mot,{target:t.id,sev});
      case 'smear':return smear(g,t,year,mot,null,null);
      case 'sow':return sowDiscord(g,t,year,mot);
      case 'offense':return offense(g,t,year,mot);
      case 'strike':return strike([g],t,year,mot,null);
    }
  }
  function actFriend(g,d,year){const t=d.target;if(!t.alive)return;return friendAct(g,t,`${mlabel(d)} for ${t.name}`,year,d);}
  function friendAct(g,t,mot,year,d){
    if(!t.alive||t===g)return;
    const C=[];
    const gd=grudgeVs(g.id,t.id);
    if(gd)C.push({w:2+(T(g,'forgiving')?2:0)-(T(g,'vengeful')?1.5:0),run:()=>{if(!decide(g,d,[{type:'GRUDGE',score:gd.sev}],year,`forgive ${t.name}`))return;forgive(g,t,gd,year,mot);}});
    const w=wars.find(w=>!w.over&&(w.sideA.includes(t.id)||w.sideB.includes(t.id))&&!w.sideA.includes(g.id)&&!w.sideB.includes(g.id));
    if(w)C.push({w:1.6,run:()=>{const side=w.sideA.includes(t.id)?w.sideA:w.sideB,en=gods[(side===w.sideA?w.sideB:w.sideA)[0]];
      if(!decide(g,d,oppHarm(g,en),year,`join ${poss(t.name)} war`))return;side.push(g.id);bump(g.id,t.id,2);bump(t.id,g.id,2);
      L(year,'JOINS WAR',`${g.name} joined ${w.eventId} beside ${t.name}  [${mot}]`,'war','conflict');addGrudge(en.id,'god',g.id,3,null,year);}});
    const ag=aggressorsOf(t.id,year).filter(a=>a!==g&&!friend(g.id,a.id));
    if(ag.length)C.push({w:2.2,run:()=>{const a=ag[0];
      const co=alive().filter(o=>o!==g&&o!==t&&o!==a&&friend(o.id,t.id)>=3);
      if(co.length&&!groups.some(q=>!q.dissolved&&q.principle.kind==='PROTECT'&&q.principle.protect===t.id)&&R.chance(.5)){if(foundGroup([g,...co.slice(0,2)],{kind:'PROTECT',protect:t.id,target:null},year,mot))return;}
      if(!decide(g,d,oppHarm(g,a),year,`avenge ${t.name}`))return;bump(t.id,g.id,2);strike([g],a,year,mot+` (defending ${t.name})`,null);}});
    const ks=secrets.filter(s=>!s.exposed&&s.keepers.has(g.id)&&!s.keepers.has(t.id)&&!s.knowers.has(t.id)&&s.group==null);
    if(ks.length)C.push({w:1,run:()=>{const s=R.pick(ks);s.knowers.add(t.id);bump(t.id,g.id,2);bump(g.id,t.id,1);L(year,'CONFIDES',`${g.name} confided ${s.id} to ${t.name}  [${mot}]`,'lie','secrets');}});
    const foes=alive().filter(x=>x!==g&&x!==t&&grudgeScore(g.id,x.id)>=3&&grudgeScore(t.id,x.id)>=3&&!groupsOf(g.id).some(q=>q.principle.target===x.id));
    if(foes.length)C.push({w:1.4,run:()=>{const x=R.pick(foes);const deadly=grudgeScore(g.id,x.id)>=8&&grudgeScore(t.id,x.id)>=8;foundGroup([g,t],{kind:deadly?'KILL':'OPPOSE',target:x.id},year,mot);}});
    if(!C.length)return;
    R.weighted(C).run();
  }

  function actTrait(g,d,year){
    const mot=mlabel(d),others=alive().filter(o=>o!==g);
    const weakest=c=>c.sort((a,b)=>a.power-b.power)[0];
    switch(d.trait){
      case 'progenitor':return createGod(g,'progenitor',year,mot);
      case 'secretive':{
        if(R.chance(.4))return seclude(g,year,mot);
        const own=events.filter(e=>e.actors.includes(g.id)&&!e.secretRef&&!story[e.id+'|'+g.id]&&['offense','strike','war_start','group_found','creation','kill_attempt'].includes(e.type));
        if(own.length){const ev=R.pick(own);return makeLie(ev,g.id,'HIDE_SELF','OMIT_ACTOR',{actor:null},null,`${ev.targets.length?names(ev.targets):'others'} remember who it was`,year,{indent:0});}
        return seclude(g,year,mot);}
      case 'obfuscator':{
        const peers=others.filter(o=>alignScore(o,{kind:'VEIL'})>=3&&!T(o,'historian'));
        if(peers.length&&R.chance(.25)&&!groupsOf(g.id).some(q=>q.principle.kind==='VEIL'))return foundGroup([g,...peers.slice(0,2)],{kind:'VEIL'},year,mot);
        return obfuscate(g,year,mot,null,null);}
      case 'historian':{
        const peers=others.filter(o=>T(o,'historian'));
        if(peers.length&&R.chance(.25)&&!groupsOf(g.id).some(q=>q.principle.kind==='RECORD'))return foundGroup([g,...peers.slice(0,2)],{kind:'RECORD'},year,mot);
        const sec=secrets.filter(s=>!s.exposed&&!s.keepers.has(g.id));const ls=lies.filter(l=>!l.exposed&&!l.tellers.includes(g.id));
        if(sec.length&&(R.chance(.5)||!ls.length))return investigate([g],R.pick(sec),year,mot,null);
        if(ls.length)return correct([g],R.pick(ls),year,mot,null);return;}
      case 'vengeful':{const gs=grudges.filter(x=>!x.resolved&&x.holder===g.id&&x.sev<10);if(gs.length){const x=gs.sort((a,b)=>b.sev-a.sev)[0];x.sev++;L(year,'BROODS',`${g.name} broods on ${x.id}; sev=${x.sev}  [${mot}]`,'grudge','conflict');}return;}
      case 'forgiving':{const gs=grudges.filter(x=>!x.resolved&&x.holder===g.id&&x.tt==='god'&&gods[x.target].alive);if(gs.length){const x=R.pick(gs);if(!decide(g,d,[{type:'GRUDGE',score:x.sev}],year,`forgive ${nm(x.target)}`))return;return forgive(g,gods[x.target],x,year,mot);}return;}
      case 'proud':{
        const won=events.filter(e=>((e.type==='war_end'&&e.data.outcome==='victory')||e.type==='deicide')&&e.actors[0]===g.id&&e.actors.length>1&&!story[e.id+'|'+g.id]&&!e.secretRef);
        if(won.length&&R.chance(.6)){const ev=R.pick(won);return makeLie(ev,g.id,'INFLATE_DEEDS','OMIT_ALLIES',{allies:[]},null,`${names(ev.actors.slice(1))} were there too`,year,{indent:0});}
        const c=others.filter(o=>o.power<g.power&&conflictItems(g,o).length);
        if(c.length){const t=R.pick(c);if(!decide(g,d,oppHarm(g,t),year,`flaunt what ${t.name} despises`))return;return offense(g,t,year,mot+' (flaunting)');}return;}
      case 'ambitious':{
        if(g.power>=10&&R.chance(.15))return createGod(g,'servant',year,mot);
        const c=others.filter(o=>o.power<g.power);if(!c.length)return;const t=weakest(c);
        if(!decide(g,d,oppHarm(g,t),year,`take power from ${t.name}`))return;return strike([g],t,year,mot,null);}
      case 'loyal':{const gs=groupsOf(g.id);if(gs.length&&R.chance(.6))return council(R.pick(gs),year,g,d);const f=others.filter(o=>friend(g.id,o.id));if(f.length)return friendAct(g,R.pick(f),mot,year,d);return;}
      case 'opportunist':{
        const gp=q=>sumP(q.members.map(m=>gods[m]));
        const mine=groupsOf(g.id),weakest=mine.slice().sort((a,b)=>gp(a)-gp(b))[0];
        const cands=groups.filter(q=>!q.dissolved&&!q.secret&&!q.members.includes(g.id)&&q.members.length<7&&(!weakest||gp(q)>gp(weakest)*1.15));
        if(cands.length&&R.chance(.8)){
          const q=cands.sort((a,b)=>gp(b)-gp(a))[0];
          if(weakest){if(!decide(g,d,[{type:'LOYALTY',score:weakest.loyalty[g.id]}],year,`desert ${weakest.name} for ${q.name}`))return;desert(g,weakest,year,mot+` (for stronger ${q.name})`);}
          return invite(q,g,year,T(g,'opportunist'));
        }
        const ws=wars.filter(w=>!w.over&&!w.sideA.includes(g.id)&&!w.sideB.includes(g.id));
        if(ws.length){const w=R.pick(ws);const pa=sumP(w.sideA.map(i=>gods[i])),pb=sumP(w.sideB.map(i=>gods[i]));
          const side=pa>=pb?w.sideA:w.sideB,other=side===w.sideA?w.sideB:w.sideA,en=gods[other[0]];
          if(!decide(g,d,oppHarm(g,en),year,`join the stronger side of ${w.eventId}`))return;
          side.push(g.id);L(year,'JOINS WAR',`${g.name} joined ${w.eventId} on the stronger side, beside ${names(side.filter(x=>x!==g.id))}  [${mot}]`,'war','conflict');addGrudge(en.id,'god',g.id,3,null,year);}
        return;}
      case 'zealous':{
        const done=g.answered||(g.answered=new Set());
        const offs=events.filter(e=>e.type==='offense'&&year-e.year<=60&&!done.has(e.id)&&!e.actors.includes(g.id)&&gods[e.actors[0]].alive&&g.dislikes.some(l=>key(l)===key(e.data.item))&&!e.secretRef);
        if(!offs.length)return;
        const ev=R.pick(offs);done.add(ev.id);const t=gods[ev.actors[0]];
        if(!decide(g,d,oppHarm(g,t),year,`punish ${t.name} for ${ev.id}`))return;
        return strike([g],t,year,`${mot}, punishing ${ev.id}`,null);}
      case 'reclusive':return seclude(g,year,mot);
      case 'gregarious':{
        const f=others.filter(o=>friend(g.id,o.id));
        if(!f.length){if(g.power>=10&&R.chance(.4))return createGod(g,'companion',year,mot);return;}
        return friendAct(g,R.pick(f),mot,year,d);}
      case 'paranoid':{if(!others.length)return;const t=others.slice().sort((a,b)=>getOp(g.id,a.id)-getOp(g.id,b.id))[0];return accuse(g,t,year,mot);}
      case 'protective':{
        const mine=others.filter(o=>friend(g.id,o.id)||kin(g.id,o.id));
        const hurt=mine.filter(o=>aggressorsOf(o.id,year).some(a=>a!==g));
        if(!hurt.length)return;
        const v=R.pick(hurt),a=R.pick(aggressorsOf(v.id,year).filter(x=>x!==g));
        if(!decide(g,d,oppHarm(g,a),year,`avenge ${v.name}`))return;
        const co=mine.filter(o=>o!==v&&friend(o.id,v.id)>=3);
        if(co.length&&!groups.some(q=>!q.dissolved&&q.principle.kind==='PROTECT'&&q.principle.protect===v.id)&&R.chance(.5)){
          if(foundGroup([g,...co.slice(0,2)],{kind:'PROTECT',protect:v.id,target:null},year,mot))return;}
        return strike([g],a,year,mot+` (defending ${v.name})`,null);}
      case 'devourer':{
        const c=others.filter(o=>!friend(g.id,o.id));if(!c.length)return;const t=weakest(c);
        if(!decide(g,d,oppHarm(g,t),year,`devour ${t.name}`))return;
        if(g.power>t.power)return attemptKill([g],t,year,mot,null);
        const allies=others.filter(o=>o!==t&&grudgeScore(o.id,t.id)>=3);if(allies.length)return foundGroup([g,...allies.slice(0,2)],{kind:'KILL',target:t.id},year,mot);return;}
    }
  }
  function actSecret(g,d,year){
    const s=d.secret,mot=`${mlabel(d)} (${s.id})`;
    const th=[...s.threats].filter(x=>gods[x].alive&&x!==g.id);
    if(th.length&&R.chance(.6)){const t=gods[R.pick(th)];if(!decide(g,d,oppHarm(g,t),year,`silence ${t.name}`))return;return strike([g],t,year,mot+' silencing a seeker',null);}
    if(s.importance>=7&&s.group==null&&!groups.some(q=>!q.dissolved&&q.principle.kind==='GUARD'&&q.principle.secretId===s.id)){
      const ks=[...new Set([...s.keepers,...[...s.knowers].filter(k=>friend(g.id,k)>=3)])].map(k=>gods[k]).filter(k=>k.alive&&k!==g&&!T(k,'historian'));
      if(ks.length&&!T(g,'historian')&&R.chance(.5)){if(foundGroup([g,...ks.slice(0,3)],{kind:'GUARD',secretId:s.id,target:null},year,mot))return;}
    }
    if(s.eventRef&&!story[s.eventRef+'|'+g.id]){const ev=events.find(e=>e.id===s.eventRef);return makeLie(ev,g.id,'KEEP_SECRET','OMIT_ACTOR',{actor:null},null,`those who were there remember`,year,{indent:0});}
    if(g.power>=10&&s.importance>=8&&R.chance(.15))return createGod(g,'keeper',year,mot,{secret:s});
    if(R.chance(.25))return seclude(g,year,mot);
  }

  /* ---- upkeep ---- */
  function upkeep(year){
    const al=alive();
    for(const a of al)for(const b of al){if(a===b)continue;const r=T(a,'forgiving')?.08:T(a,'vengeful')?.03:.05;const o=getOp(a.id,b.id);setOp(a.id,b.id,o+(base(a.id,b.id)-o)*r);}
    for(const g of grudges)if(!g.resolved&&T(gods[g.holder],'vengeful')&&g.sev<10&&R.chance(.02*T(gods[g.holder],'vengeful')/5)){g.sev++;if(g.sev>=8)L(year,'FESTERS',`${g.id} ${nm(g.holder)} → ${tlabel(g.tt,g.target)} festers, sev=${g.sev}`,'grudge','conflict');}
    for(const g of al)if(R.chance(.015+T(g,'ambitious')*.002)){g.power++;}
  }

  /* ---- year loop ---- */
  for(let year=1;year<=YEARS;year++){
    if(year===1)birthGlory(year);
    for(const pc of pendingCorpses)if(pc.year===year)emergeCorpse(pc,year);
    if(year>15&&alive().length&&alive().length<14&&R.chance(.004))birthNowhere(year);
    for(const w of wars)if(!w.over&&w.endYear===year)endWar(w,year);
    upkeep(year);
    if(!alive().length)break;
    for(const g of R.shuffle(alive())){
      if(!active(g,year))continue;
      const p=g.progenitor>0?.3:ACT_P;
      if(R.chance(p))godAct(g,year);
    }
    for(const gr of groups)if(!gr.dissolved&&R.chance(.05))council(gr,year,null,null);
  }

  /* ---- Altus regions and locations ---- */
  const live=alive().sort((a,b)=>b.power-a.power);
  S.regions.push({name:'The Woods',status:'unclaimed; the same in every world'});
  const claim=(name,pool,places)=>{
    if(!pool.length){S.regions.push({name,status:'unclaimed'});return [];}
    const hs=(pool[1]&&pool[0].power-pool[1].power<=2)?[pool[0],pool[1]]:[pool[0]];
    S.regions.push({name,status:hs.length>1?`contested by ${hs.map(h=>h.name).join(' and ')}`:`held by ${hs[0].name}`});
    for(const h of hs){
      const place=R.pick(places);const ev=newEvent('location',YEARS,[h.id],[],{place,region:name});
      L(YEARS,'LOCATION',`${ev.id} ${poss(h.name)} sanctum lies ${place}`,'event','misc');
      if(T(h,'secretive'))addSecret({kind:'LOCATION',about:h.id,text:`The door to ${poss(h.name)} sanctum lies ${place}.`,keepers:[h.id],importance:T(h,'secretive'),eventRef:ev.id},YEARS);
      const liar=R.shuffle(alive()).find(x=>x!==h&&(T(x,'obfuscator')||((T(x,'secretive')||T(x,'paranoid'))&&grudgeScore(x.id,h.id)>0)));
      if(liar&&R.chance(.75))makeLie(ev,liar.id,T(liar,'obfuscator')?'OBFUSCATE':'LURE_MORTALS','FABRICATE_LOCATION',{place:R.pick(WOODS_PLACES)},h.id,`${poss(h.name)} own account gives the true door ${place}`,YEARS,{indent:0});
    }
    return hs;
  };
  L(YEARS,'ALTUS','region claims computed','head','misc');
  const m=claim('The Mountain',live,MOUNTAIN_PLACES);
  claim('The House on the mountainside',live.filter(g=>!m.includes(g)),HOUSE_PLACES);
  for(const gr of groups)if(gr.hall){const hs=purposeSecret(gr);S.regions.push({name:`Hall of ${gr.name}`,status:`${gr.dissolved?'abandoned':'kept by its members'}${gr.secret&&hs&&!hs.exposed?'; hidden':''}`});}
  for(const d of S.deicides)S.regions.push({name:`Ruins of ${nm(d.targets[0])}`,status:`a broken shrine on the Mountain's northern slope, from the ${ord(d.year)} year`});

  /* ---- tensions ---- */
  for(const g of grudges)if(!g.resolved&&g.sev>=5&&gods[g.holder].alive&&(g.tt==='group'?!groups[g.target].dissolved:gods[g.target].alive))S.tensions.push(`${g.id}: ${nm(g.holder)} holds a grudge against ${tlabel(g.tt,g.target)} (sev ${g.sev})`);
  for(const gr of groups.filter(q=>!q.dissolved)){
    const unhappy=gr.members.filter(mm=>(groupGrudge(mm,gr.id)||{sev:0}).sev>=gr.loyalty[mm]);
    if(unhappy.length)S.tensions.push(`${gr.name} is strained: ${names(unhappy)} resent${unhappy.length===1?'s':''} it more than ${unhappy.length===1?'it is':'they are'} loyal`);
    if(gr.principle.kind==='KILL'&&gods[gr.principle.target].alive)S.tensions.push(`${gr.name} still plots to kill ${nm(gr.principle.target)}${gr.secret?' (in secret)':''}`);
  }
  for(const w of wars)if(!w.over)S.tensions.push(`${w.eventId}: war between ${names(w.sideA)} and ${names(w.sideB)} is unresolved`);
  const hiddenS=secrets.filter(s=>!s.exposed).length;
  if(hiddenS)S.tensions.push(`${hiddenS} secret${hiddenS>1?'s':''} still kept`);
  const liesLeft=lies.filter(l=>!l.exposed).length;
  if(liesLeft)S.tensions.push(`${liesLeft} lie${liesLeft>1?'s':''} still believed`);
  return S;
}

/* ================= text rendering ================= */
function renderEvent(ev,reveals,lie,S){return cap(renderEvent0(ev,reveals,lie,S));}
function renderEvent0(ev,reveals,lie,S){
  const G=S.gods,n=id=>G[id].name,names=S.names,c=lie?lie.claimed:{};
  const grn=id=>S.groups[id].name;
  const yr=(lie&&c.year)||ev.year;
  const when=reveals==='WHEN'?`In the ${ord(yr)} year, `:'';
  const W=s=>when?when+s:cap(s);
  const act=()=>('actor' in c)?(c.actor===null?null:[c.actor]):ev.actors;
  const A=()=>{const a=act();return a===null?'an unseen hand':names(a);};
  switch(ev.type){
    case 'glory_birth':return W(`${n(ev.actors[0])} came from glory, and made the world.`);
    case 'creation':{const why={progenitor:'in the first days',champion:`to be its champion against ${ev.data.target!=null?n(ev.data.target):'its enemies'}`,companion:'so that it would not be alone',keeper:'to keep what it had hidden',servant:'to serve its ambitions'}[ev.data.reason]||'';return W(`${n(ev.actors[0])} made ${n(ev.targets[0])} ${why}.`);}
    case 'corpse_birth':return W(`${n(ev.actors[0])} rose from the corpse of ${n(ev.targets[0])}.`);
    case 'nowhere_birth':return W(`${n(ev.actors[0])} came from nowhere, and none know why.`);
    case 'offense':{const V=n(ev.targets[0]),ap=actPhrase(ev.data.item);
      if(reveals==='WHY')return `${cap(A())} ${ap}, though ${V} despises it. Why? ${cap(c.cause||`${A()} cared nothing for what ${V} holds sacred`)}.`;
      if(reveals==='WHO'&&act())return `It was ${A()} who ${ap}, a thing ${V} despises.`;
      return W(`${A()} ${ap}, a thing ${V} despises.`);}
    case 'strike':{const Tn=n(ev.targets[0]);const gt=(ev.data.group!=null&&!('actor' in c))?`, acting as ${grn(ev.data.group)},`:'';
      if(reveals==='WHY'&&c.cause)return `${cap(A())} struck ${Tn}, because ${c.cause}.`;
      if(reveals==='WHO'&&act())return `It was ${A()}${gt} who ${ev.data.success?'wounded':'struck at'} ${Tn}.`;
      return W(`${A()}${gt} ${ev.data.success?`struck ${Tn} and wounded them`:`struck at ${Tn} and failed`}.`);}
    case 'kill_attempt':return W(`${A()} tried to kill ${n(ev.targets[0])}, and failed.`);
    case 'deicide':{let k=act();if(k&&lie&&lie.distortion==='OMIT_ALLIES')k=[ev.actors[0]];const who=k?names(k):'an unseen hand',Tn=n(ev.targets[0]);
      if(reveals==='OUTCOME')return `After ${who} struck, ${Tn} was no more, and all that ${Tn} loved went unclaimed.`;
      return W(`${who} slew ${Tn}, and ${Tn} is no more.`);}
    case 'war_start':return reveals==='WHY'?`${n(ev.actors[0])} made war on ${n(ev.targets[0])} out of old hatred, and ${names(ev.actors.concat(ev.targets).slice(2))||'none'} took sides.`.replace(', and none took sides',''):W(`war came: ${names(ev.actors)} against ${names(ev.targets)}.`);
    case 'war_end':{const all=[...ev.actors,...ev.targets];
      if(lie&&lie.distortion==='INVERT_OUTCOME'&&c.outcome==='stalemate')return W(`the war of ${names(all)} ended with neither side holding the field.`);
      if(lie&&lie.distortion==='INVERT_OUTCOME'&&c.outcome==='victory'){const other=ev.actors.includes(c.winner)?ev.targets:ev.actors;return W(`${n(c.winner)} won the war against ${names(other)}.`);}
      if(lie&&lie.distortion==='OMIT_ALLIES')return W(`${n(ev.actors[0])} alone broke ${names(ev.targets)}.`);
      if(ev.data.outcome==='stalemate')return W(`the war of ${names(all)} ended with neither side holding the field.`);
      return W(`${names(ev.actors)} broke ${names(ev.targets)}${ev.data.decisive?' utterly':''}.`);}
    case 'forgive':return W(`${n(ev.actors[0])} forgave ${n(ev.targets[0])}.`);
    case 'seclusion':return reveals==='WHEN'?`In the ${ord(yr)} year, ${n(ev.actors[0])} withdrew ${ev.data.place} and was not seen for ${ev.data.dur} years.`:`${n(ev.actors[0])} withdrew ${ev.data.place}.`;
    case 'accusation':return W(`${n(ev.actors[0])} accused ${n(ev.targets[0])} of plotting against it.`);
    case 'group_found':{const gr=S.groups[ev.data.group];
      if(reveals==='WHY')return `${names(ev.actors)} founded ${gr.name}, sworn ${principlePhrase(gr.principle,S)}.`;
      return W(`${names(ev.actors)} founded ${gr.name}${ev.data.leader!=null?`, with ${n(ev.data.leader)} at its head`:', and named no leader'}.`);}
    case 'group_join':return W(`${n(ev.actors[0])} joined ${grn(ev.data.group)}.`);
    case 'group_expel':return W(`${grn(ev.data.group)} cast out ${n(ev.targets[0])}.`);
    case 'defect':return W(`${n(ev.actors[0])} walked away from ${grn(ev.data.group)}.`);
    case 'splinter':return W(`${names(ev.actors)} broke from ${grn(ev.data.group)}${ev.data.newGroup!=null?` and founded ${grn(ev.data.newGroup)}`:''}.`);
    case 'betrayal':return W(`${n(ev.actors[0])} betrayed ${grn(ev.data.group)}${ev.data.leaked?' and laid bare its secrets':''}.`);
    case 'hall_built':return W(`${grn(ev.data.group)} raised a hall in the Altus.`);
    case 'exposure':{const s=S.secrets.find(x=>x.id===ev.data.secret);return W(`${ev.data.group!=null?grn(ev.data.group):names(ev.actors)} uncovered a hidden thing: ${lc(s.text)}`);}
    case 'correction':{const l=S.lies.find(x=>x.id===ev.data.lie);const e=S.events.find(x=>x.id===l.eventId);return W(`${ev.data.group!=null?grn(ev.data.group):names(ev.actors)} proved that ${poss(n(l.teller))} account of the ${ord(e.year)} year was false.`);}
    case 'location':return `The door to ${poss(n(ev.actors[0]))} sanctum lies ${c.place||ev.data.place}.`;
  }
  return '';
}
function claimedParticipants(ev,lie){
  let p;
  if(ev.type==='location')p=ev.actors.slice();else p=[...ev.actors,...ev.targets];
  if(!lie)return p;
  const c=lie.claimed;
  if(lie.distortion==='SWAP_ACTOR')p=[c.actor,...ev.targets];
  else if(lie.distortion==='OMIT_ACTOR')p=[...ev.targets];
  else if(lie.distortion==='OMIT_ALLIES')p=[ev.actors[0],...ev.targets];
  return p;
}

/* ================= fragments ================= */
const REVEALS={offense:['WHO','WHY','WHEN'],strike:['WHO','WHEN'],kill_attempt:['WHO','WHEN'],deicide:['WHO','OUTCOME','WHEN'],war_start:['WHO','WHY','WHEN'],war_end:['OUTCOME','WHO'],creation:['WHO','WHEN'],corpse_birth:['WHO','WHEN'],nowhere_birth:['WHO','WHEN'],glory_birth:['WHO'],group_found:['WHO','WHY','WHEN'],group_join:['WHO'],group_expel:['WHO'],defect:['WHO'],splinter:['WHO','WHEN'],betrayal:['WHO'],hall_built:['WHO'],exposure:['WHO'],correction:['WHO'],forgive:['WHO'],seclusion:['WHEN','LOCATION'],accusation:['WHO'],location:['LOCATION']};
const FW={creation:3,corpse_birth:4,nowhere_birth:3,glory_birth:2,offense:2,strike:1.5,kill_attempt:4,deicide:6,war_start:2,war_end:3,group_found:3,group_join:1,group_expel:2,defect:1.5,splinter:3,betrayal:4,hall_built:2,exposure:4,correction:3,forgive:1,seclusion:.6,accusation:1,location:3};
const COMPLEX={offense:.05,strike:.05,kill_attempt:.2,deicide:.4,war_start:.1,war_end:.2,betrayal:.2,splinter:.15,group_found:.1,exposure:.15,location:.15,creation:.1,corpse_birth:.2};
const EFFECTS=[
  ["Instantly grow all planted crops nearby to full","Gain favor with a chosen god","Locate a newly generated structure","Mend every tool in your inventory"],
  ["Clear or call the weather","Repel hostile mobs from the area for one day","Extend your next visit to the Altus","Reveal the nearest dungeon tome"],
  ["Bring one item of your choosing into the Altus","Add strength to one side of the coming event","Nudge one god's opinion of another","Open a sealed door in the Altus for one visit"]
];
function revealFor(ev,lie,R){
  const base=REVEALS[ev.type]||['WHO'];if(!lie)return R.pick(base);
  const ok={CHANGE_CAUSE:['WHY'],FABRICATE_LOCATION:['LOCATION'],SHIFT_DATE:['WHEN']}[lie.distortion];
  if(lie.distortion==='SHIFT_DATE')return 'WHEN';
  const opts=ok?base.filter(r=>ok.includes(r)):base;return R.pick(opts.length?opts:base);
}
const ACTION_NAMES={mine_ore:'mining ore',fell_tree:'felling trees',till_soil:'tilling the soil',dig_deep:'digging deep',kill_passive:'killing gentle beasts',kill_hostile:'hunting the creatures of the night',breed_animals:'breeding the herds',craft_tool:'forging tools',brew_potion:'brewing potions',enchant_item:'enchanting iron',burn_item:'burning offerings',light_fire:'kindling fires',extinguish_fire:'quenching fires',sleep_in_nether:'sleeping in the burning places',build_height:'building toward the sky'};
const TRAIT_ADJ={secretive:'secretive',obfuscator:'a deceiver of mortals',historian:'a keeper of the true record',mad:'touched by madness',vengeful:'vengeful',forgiving:'forgiving',proud:'proud',ambitious:'ambitious',loyal:'loyal',opportunist:'an opportunist',zealous:'zealous',reclusive:'reclusive',gregarious:'gregarious',paranoid:'paranoid',protective:'protective',devourer:'a devourer of lesser gods'};
const itemPhrase=l=>l.cat==='action'?(ACTION_NAMES[l.id]||l.id):l.label;
const listPhrase=a=>a.length<=1?a.join(''):a.length===2?a.join(' and '):a.slice(0,-1).join(', ')+', and '+a[a.length-1];
const lcT=s=>s.replace(/^The /,'the ');
function eventTitle(ev,S){
  const n=id=>lcT(S.gods[id].name),grn=id=>lcT(S.groups[id].name);
  switch(ev.type){
    case 'glory_birth':return 'The Making of the World';
    case 'creation':return `The Making of ${n(ev.targets[0])}`;
    case 'corpse_birth':return `The Rising of ${n(ev.actors[0])}`;
    case 'nowhere_birth':return `The Coming of ${n(ev.actors[0])}`;
    case 'offense':return `An Offense Against ${n(ev.targets[0])}`;
    case 'strike':return `A Blow Against ${n(ev.targets[0])}`;
    case 'kill_attempt':return `An Attempt on the Life of ${n(ev.targets[0])}`;
    case 'deicide':return `The Death of ${n(ev.targets[0])}`;
    case 'war_start':return `The War of ${n(ev.actors[0])} and ${n(ev.targets[0])}`;
    case 'war_end':return `The End of the War of ${n(ev.actors[0])} and ${n(ev.targets[0])}`;
    case 'forgive':return `The Forgiveness of ${n(ev.targets[0])}`;
    case 'seclusion':return `The Withdrawal of ${n(ev.actors[0])}`;
    case 'accusation':return `An Accusation Against ${n(ev.targets[0])}`;
    case 'group_found':return `The Founding of ${grn(ev.data.group)}`;
    case 'group_join':return `The Joining of ${grn(ev.data.group)}`;
    case 'group_expel':return `The Casting Out of ${n(ev.targets[0])}`;
    case 'defect':return `A Departure from ${grn(ev.data.group)}`;
    case 'splinter':return `The Breaking of ${grn(ev.data.group)}`;
    case 'betrayal':return `The Betrayal of ${grn(ev.data.group)}`;
    case 'hall_built':return `The Hall of ${grn(ev.data.group)}`;
    case 'exposure':return 'A Thing Laid Bare';
    case 'correction':return 'A Falsehood Undone';
    case 'location':return `The Door to the Sanctum of ${n(ev.actors[0])}`;
  }
  return 'A Fragment of the Past';
}
const SECRET_TITLE={EXISTENCE:'A Hidden God',DEED:'A Hidden Deed',GROUP_PURPOSE:'A Hidden Purpose',MEMBERSHIP:'A Hidden Allegiance',LOCATION:'A Hidden Place'};

function genStatic(S,seed,R,hiddenEx){
  const {gods,groups,grudges,secrets}=S,n=id=>gods[id].name;
  const out=[],used=new Set();let slc=0;
  const vis=g=>!hiddenEx.has(g.id);
  const att=t=>t.alive?`So ${t.name} tells it.`:`So ${t.name}, who is no more, once told it.`;
  const mkLie=(t,distortion,contra)=>({id:'SL-'+String(++slc).padStart(3,'0'),motive:'OBFUSCATE',distortion,contra,teller:t.id,coordinated:null,exposed:false});
  const chooseTeller=(subjects,prefer)=>{
    const cand=gods.filter(vis).map(g=>{let w=.5;if(subjects.includes(g.id))w=3;else if(subjects.some(s=>S.getOp(g.id,s)>=3))w=1.5;if(prefer&&prefer.includes(g.id))w+=2;if(g.traits.historian)w+=1.5;if(g.traits.secretive)w*=.3;return {w,g};});
    const c=R.weighted(cand);return c?c.g:null;
  };
  const pool=[...BLOCKS.map(b=>({cat:'block',id:b[0],label:b[1]})),...ENTITIES.map(e=>({cat:'entity',id:e[0],label:e[1]}))];
  const kinds=[{k:'LIKES',w:2},{k:'DISLIKES',w:2},{k:'OPINION',w:3},{k:'GRUDGE',w:3},{k:'NATURE',w:1.5},{k:'GROUP',w:2}];
  const push=(kind,key2,title,text,teller,lie,reveals)=>{
    if(used.has(key2))return false;used.add(key2);
    const f={static:true,kind,title,text:cap(text),teller,lie,reveals,att:att(teller)};
    if(lie&&R.chance(.6)){const t=Object.keys(teller.traits).find(t=>TELLS[t]);if(t)f.tell=TELLS[t];}
    out.push(f);return true;
  };
  let tries=0;
  while(out.length<6&&tries<120){
    tries++;
    const k=R.weighted(kinds).k;
    if(k==='LIKES'||k==='DISLIKES'){
      const dis=k==='DISLIKES',xs=gods.filter(vis);if(!xs.length)continue;
      const x=R.pick(xs),teller=chooseTeller([x.id]);if(!teller)continue;
      const src=R.shuffle(dis?x.dislikes:x.likes);let shown=src.slice(0,Math.min(src.length,R.int(2,3)));let lie=null;
      if(teller.traits.obfuscator&&R.chance(.6)){
        const lr=makeRng(seed,'sl|'+teller.id+'|'+x.id+'|'+k);
        const other=lr.shuffle(pool).find(it=>!x.likes.some(l=>key(l)===key(it))&&!x.dislikes.some(l=>key(l)===key(it)));
        if(other){shown=shown.slice();shown[lr.int(0,shown.length-1)]=other;lie=mkLie(teller,'SWAP_ITEM',`${poss(x.name)} own deeds, and any other account of what it ${dis?'despises':'loves'}`);}
      }
      const items=listPhrase(shown.map(itemPhrase));
      push(k,k+'|'+x.id,dis?`The Hatreds of ${lcT(x.name)}`:`The Loves of ${lcT(x.name)}`,dis?`${x.name} cannot abide ${items}.`:`${x.name} delights in ${items}.`,teller,lie,k);
    }else if(k==='OPINION'){
      const prs=[];for(const a of gods)for(const b of gods){if(a===b||!a.alive||!b.alive||!vis(a)||!vis(b))continue;const o=Math.round(S.getOp(a.id,b.id));if(Math.abs(o)>=3)prs.push({w:Math.abs(o),a,b,o});}
      if(!prs.length)continue;
      const c=R.weighted(prs),teller=chooseTeller([c.a.id],[c.a.id]);if(!teller)continue;
      let o=c.o,lie=null;
      if(teller.traits.obfuscator&&R.chance(.6)){o=-o;lie=mkLie(teller,'INVERT_FEELING',`${poss(c.a.name)} own deeds toward ${c.b.name}`);}
      const verb=o>=5?'holds':o>=3?'thinks well of':o<=-5?'despises':'distrusts';
      push(k,k+'|'+c.a.id+'|'+c.b.id,`What ${lcT(c.a.name)} Thinks of ${lcT(c.b.name)}`,o>=5?`${c.a.name} holds ${c.b.name} dear.`:`${c.a.name} ${verb} ${c.b.name}.`,teller,lie,k);
    }else if(k==='GRUDGE'){
      const gs=grudges.filter(g=>!g.resolved&&gods[g.holder].alive&&vis(gods[g.holder])&&(g.tt==='god'?gods[g.target].alive&&vis(gods[g.target]):(()=>{const q=groups[g.target];if(q.dissolved)return false;if(!q.secret)return true;const ps=secrets.find(s=>s.kind==='GROUP_PURPOSE'&&s.group===q.id);return ps&&ps.exposed;})()));
      if(!gs.length)continue;
      const gd=R.pick(gs),holder=gods[gd.holder],teller=chooseTeller([holder.id],[holder.id]);if(!teller)continue;
      let tName=gd.tt==='god'?n(gd.target):groups[gd.target].name,lie=null,why='';
      if(gd.items&&gd.items.length)why=`, for ${tName} likes ${listPhrase(gd.items.map(itemPhrase))}, which ${holder.name} despises`;
      else if(gd.cause){const e=S.events.find(x=>x.id===gd.cause);if(e)why=`, born of the events of the ${ord(e.year)} year`;}
      if(teller.traits.obfuscator&&gd.tt==='god'&&R.chance(.6)){
        const others=gods.filter(g=>vis(g)&&g.id!==holder.id&&g.id!==gd.target);
        if(others.length){const z=makeRng(seed,'sl|'+teller.id+'|'+gd.id).pick(others);tName=z.name;why='';lie=mkLie(teller,'SWAP_TARGET',`${holder.name} and ${n(gd.target)} both know whom the grudge is truly against`);}
      }
      const adj=gd.sev>=8?'bitter':gd.sev>=5?'deep':'lingering';
      push(k,k+'|'+gd.id,`The Grudge of ${lcT(holder.name)}`,`${holder.name} holds a ${adj} grudge against ${tName}${why}.`,teller,lie,k);
    }else if(k==='NATURE'){
      const xs=gods.filter(vis);if(!xs.length)continue;
      const x=R.pick(xs),teller=chooseTeller([x.id]);if(!teller)continue;
      let ts=Object.keys(x.traits),lie=null;
      if(teller.traits.obfuscator&&R.chance(.6)){
        const lr=makeRng(seed,'sl|'+teller.id+'|'+x.id+'|N');const fake=lr.shuffle(TRAIT_NAMES).find(t=>!(t in x.traits)&&!EXCL.some(pr=>pr.includes(t)&&ts.some(u=>pr.includes(u))));
        if(fake){ts=ts.slice();ts[lr.int(0,ts.length-1)]=fake;lie=mkLie(teller,'SWAP_TRAIT',`${poss(x.name)} own deeds`);}
      }
      push(k,k+'|'+x.id,`The Nature of ${lcT(x.name)}`,`${x.name} is said to be ${listPhrase(ts.map(t=>TRAIT_ADJ[t]||t))}.`,teller,lie,k);
    }else if(k==='GROUP'){
      const gs=groups.filter(q=>!q.dissolved&&q.members.length>=2&&q.members.every(m=>vis(gods[m]))&&(!q.secret||(()=>{const ps=secrets.find(s=>s.kind==='GROUP_PURPOSE'&&s.group===q.id);return ps&&ps.exposed;})()));
      if(!gs.length)continue;
      const q=R.pick(gs),teller=chooseTeller(q.members,q.members);if(!teller)continue;
      let ms=q.members.slice(),lie=null;
      if(teller.traits.obfuscator&&!q.members.includes(teller.id)&&R.chance(.6)){
        const others=gods.filter(g=>vis(g)&&!q.members.includes(g.id));
        if(others.length){const lr=makeRng(seed,'sl|'+teller.id+'|'+q.id);ms[lr.int(0,ms.length-1)]=lr.pick(others).id;lie=mkLie(teller,'SWAP_MEMBER',`the group's own members know who sits among them`);}
      }
      const ldr=q.leader!=null&&q.members.includes(q.leader)?`${n(q.leader)} leads it.`:'It has no leader.';
      push(k,k+'|'+q.id,`On ${lcT(q.name)}`,`${q.name} is sworn ${principlePhrase(q.principle,S)}. ${ldr} Its members are ${listPhrase(ms.map(n))}.`,teller,lie,k);
    }
  }
  return out;
}

function genFragments(S,seed){
  const R=makeRng(seed,'fragments'),{events,gods,story,secrets}=S;
  const hiddenEx=new Set(secrets.filter(s=>s.kind==='EXISTENCE'&&!s.exposed).map(s=>s.about));
  const hidden=e=>{if([...e.actors,...e.targets].some(x=>hiddenEx.has(x)))return true;if(!e.secretRef)return false;const s=secrets.find(x=>x.id===e.secretRef);return s&&!s.exposed;};
  const frags=[],used=new Set(),perEv={},pool=events.filter(e=>FW[e.type]&&!hidden(e));
  let tries=0;
  while(frags.length<12&&tries<600&&pool.length){
    tries++;
    const ev=R.weighted(pool.map(e=>({w:FW[e.type]*(gods.some(g=>story[e.id+'|'+g.id])?2:1),e}))).e;
    const inv=new Set([...ev.actors,...ev.targets]);const cand=[];
    for(const g of gods){
      if(g.born>ev.year||hiddenEx.has(g.id))continue;
      if(g.deathYear!=null&&(g.deathYear<ev.year||(ev.type==='deicide'&&ev.targets.includes(g.id))))continue;
      let w=inv.has(g.id)?3:([...inv].some(x=>S.getOp(g.id,x)>=3)?1:.3);
      if(g.traits.historian)w+=1.5;if(g.traits.secretive)w*=.3;if(story[ev.id+'|'+g.id])w+=3;
      cand.push({w,g});
    }
    if(!cand.length)continue;
    const teller=R.weighted(cand).g,lie=story[ev.id+'|'+teller.id]||null,reveals=revealFor(ev,lie,R);
    const k=ev.id+'|'+teller.id+'|'+reveals;if(used.has(k)||(perEv[ev.id]||0)>=2)continue;used.add(k);perEv[ev.id]=(perEv[ev.id]||0)+1;
    frags.push(makeFragment(ev,teller,reveals,lie,S,R));
  }
  // secrets
  const sp=R.shuffle(secrets).sort((a,b)=>(b.exposed?1:0)-(a.exposed?1:0)).slice(0,6);
  let ns=0;
  for(const s of sp){
    if(ns>=3)break;
    if(s.kind==='EXISTENCE'&&!s.exposed)continue;
    let teller=null,src='';
    const aliveK=[...s.keepers].map(k=>gods[k]).filter(g=>g.alive);
    if(s.exposed&&s.exposer!=null){teller=gods[s.exposer];src='exposed';}
    else if([...s.knowers].length){teller=gods[R.pick([...s.knowers])];src='knower';}
    else{const mad=aliveK.find(g=>g.traits.mad);if(mad){teller=mad;src='slip';}else if(R.chance(.5))src='glimpse';else continue;}
    ns++;
    frags.push({secret:s,src,teller,title:SECRET_TITLE[s.kind]||'A Hidden Truth',text:s.text,reveals:'SECRET',
      att:teller?(src==='slip'?`${teller.name} let this slip, and did not seem to know it.`:`So ${teller.name} tells it.`):'Glimpsed, unbidden, in the Altus.'});
  }
  // static knowledge
  for(const f of genStatic(S,seed,R,hiddenEx))frags.push(f);
  frags.forEach(f=>f.no=1000+R.int(0,8999));
  return frags;
}
function makeFragment(ev,teller,reveals,lie,S,R){
  const G=S.gods;
  const f={ev,teller,reveals,lie,title:eventTitle(ev,S),text:renderEvent(ev,reveals,lie,S)};
  if(lie&&R.chance(.6)){const t=Object.keys(teller.traits).find(t=>TELLS[t]);if(t)f.tell=TELLS[t];}
  f.att=teller.alive?`So ${teller.name} tells it.`:`So ${teller.name}, who is no more, once told it.`;
  const claimed=[...new Set(claimedParticipants(ev,lie))];
  const comp=COMPLEX[ev.type]||0;
  if(claimed.length>=2&&R.chance(Math.min(.8,.04+.1*claimed.length+comp))){
    const score=claimed.length+comp*10;const tier=score>=6?2:score>=4?1:0;
    let effect=R.pick(EFFECTS[tier]);
    if(ev.type==='deicide'&&claimed.some(id=>!G[id].alive))effect=`Call an echo of ${G[ev.targets[0]].name} (very dangerous)`;
    const offerings=claimed.map(id=>{const g=G[id];const b=g.likes.find(l=>l.cat==='block')||g.likes[0];return {god:g.name,item:b.label};});
    const truth=[...new Set(claimedParticipants(ev,null))].sort().join(),said=claimed.slice().sort().join();
    f.ritual={tier:tier+1,effect,offerings,misfire:!!lie&&truth!==said};
  }
  return f;
}
if(typeof module!=='undefined')module.exports={simulate,genFragments,renderEvent,TRAITS_DEF,ORIGIN_LABEL,principlePhrase};
