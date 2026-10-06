const {simulate}=require('../prototype/sim.js');const fs=require('fs');
const n=+process.argv[2],prefix=process.argv[3];const out=[];
for(let i=0;i<n;i++){const s=simulate(prefix+i);out.push('### '+prefix+i);
 for(const l of s.log)out.push(l.year+'|'+l.tag+'|'+l.cls+'|'+l.cat+'|'+l.indent+'|'+l.text);
 for(const r of s.regions)out.push('REGION '+r.name+': '+r.status);
 for(const t of s.tensions)out.push('TENSION '+t);}
fs.writeFileSync(process.argv[4],out.join('\n')+'\n');
