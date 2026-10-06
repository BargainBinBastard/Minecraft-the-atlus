import io.github.bargainbinbastard.altus.history.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class CompareMany {
  public static void main(String[] a) throws Exception {
    int n=Integer.parseInt(a[0]); String prefix=a[1];
    try(Writer w=new OutputStreamWriter(new FileOutputStream(a[2]),StandardCharsets.UTF_8)){
      for(int i=0;i<n;i++){
        History s=HistorySimulator.simulate(prefix+i);
        StringBuilder b=new StringBuilder("### "+prefix+i+"\n");
        for(History.LogLine l:s.log)b.append(l.year+"|"+l.tag+"|"+l.cls+"|"+l.cat+"|"+l.indent+"|"+l.text).append('\n');
        for(History.Region r:s.regions)b.append("REGION "+r.name+": "+r.status).append('\n');
        for(String t:s.tensions)b.append("TENSION "+t).append('\n');
        w.write(b.toString());
      }
    }
  }
}
