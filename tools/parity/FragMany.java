import io.github.bargainbinbastard.altus.history.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
public class FragMany {
  public static void main(String[] a) throws Exception {
    int n=Integer.parseInt(a[0]);String prefix=a[1];
    try(Writer w=new OutputStreamWriter(new FileOutputStream(a[2]),StandardCharsets.UTF_8)){
      for(int i=0;i<n;i++){String sd=prefix+i;History s=HistorySimulator.simulate(sd);List<Fragments.Fragment> F=Fragments.generate(s);
        w.write("### "+sd+"\n");
        for(Fragments.Fragment f:F){
          String lieId=f.lie!=null?f.lie.id:(f.staticLie!=null?f.staticLie.id:"");
          String rit="";
          if(f.ritual!=null){List<String> o=new ArrayList<>();for(Fragments.Offering x:f.ritual.offerings)o.add(x.god+":"+x.item);rit=f.ritual.tier+"/"+f.ritual.effect+"/"+String.join(";",o)+"/"+f.ritual.misfire;}
          w.write(String.join("|",String.valueOf(f.no),f.source,f.kind==null?"":f.kind,f.title,f.text,f.att,f.tell==null?"":f.tell,lieId,rit)+"\n");
        }}}}}
