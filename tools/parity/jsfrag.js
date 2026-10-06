const {simulate,genFragments}=require('../prototype/sim.js');const fs=require('fs');
const n=+process.argv[2],prefix=process.argv[3];const out=[];
for(let i=0;i<n;i++){const sd=prefix+i;const s=simulate(sd);const F=genFragments(s,sd);out.push('### '+sd);
 for(const f of F){const src=f.static?'STATIC':f.secret?'SECRET':'EVENT';
  const lieId=f.lie?f.lie.id:'';
  const rit=f.ritual?(f.ritual.tier+'/'+f.ritual.effect+'/'+f.ritual.offerings.map(o=>o.god+':'+o.item).join(';')+'/'+f.ritual.misfire):'';
  out.push([f.no,src,f.kind||'',f.title,f.text,f.att,f.tell||'',lieId,rit].join('|'));}}
fs.writeFileSync(process.argv[4],out.join('\n')+'\n');
