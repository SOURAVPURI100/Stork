package stork.optimizers;

import stork.ad.*;
import stork.util.*;
import java.util.*;
import java.lang.Math.*;

public class FullCOptimizer extends Optimizer {
  List<Block> samples = new ArrayList<Block>();
  boolean warmed_up = false;
  boolean done_sampling = false, analysis_done = false;
  double last_tp = 0;
  int p_base = 0, pd = 1, para;
  long off = 0, size = -1;
  Range p_range = new Range(1, 1);

  // Little thing to hold sampling results in a nicer way.
  private class Block {
    int para = FullCOptimizer.this.para;
    long size = -1;
    double tp = 0;

    public Block() { }

    public Block(long size) {
      this.size = size;
    }

    public Ad toAd() {
      Ad ad = new Ad();
      if (size >= 0)
        ad.put("size", size);
      if (para >= 0)
        ad.put("parallelism", para);
      return ad;
    }
  }

  public String name() {
    return "full_c";
  }

  public void initialize(long size, Range range) {
    this.size = size;
    p_range = range;
    para = range.min();
    p_base = para-1;
  }

  public Ad sample() {
    if (done_sampling) {
      if (!analysis_done) doAnalysis();
      return new Block().toAd();
    }

    long sample = (long) ((size >= 5E8 || size <= 0) ? 5E7 : size/10.0);

    // Don't transfer more than what's available.
    if (off+sample >= size && size > 0)
      sample = -1;

    // Determine if this is the last sample we want.
    if (para >= p_range.max()) {
      para = p_range.max();
      done_sampling = true;
    }

    // Construct the block to transfer.
    Block b = new Block(sample);
    return b.toAd();
  }

  public void report(Ad ad) {
    Block b = new Block();

    b.para = ad.getInt("parallelism", para);
    b.size = ad.getLong("size");
    b.tp   = ad.getDouble("throughput");

    report(b);
  }

  void report(Block b) {
    if (done_sampling) return;

    off += b.size;

    // If that was a warm-up sample, don't change anything.
    if (!warmed_up) {
      warmed_up = true;
    } else {
      // Keep the sample and calculate next parallelism.
      samples.add(b);

      pd *= 2;
      para = p_base+pd;

      if (para > p_range.max()) {
        para = p_range.max();
        done_sampling = true;
      }

      if (b.tp < last_tp)
        done_sampling = true;
      last_tp = b.tp;
    }
  }

  // Get the best parallelism from the samples.
  int bestParallelism() {
    double tp = 0;
    int sc = para;

    for (Block b : samples) if (b.tp > tp) {
      tp = b.tp;
      sc = b.para;
    } return sc;
  }

  // TODO: Clean this up.
  void doAnalysis() {
    int j,k,l,m,s;
    int n1,n2,n3,n4,optn1,optn2,optn3,optn4;
    double a,b,c,d,opta=0,optb=0,optc=0,optd=0;
    double minerror=4000000;
    //  double maxerror=100;
    int point_n = 1;
    int opt_p;
    double opt_th;

    point_n = samples.size();

    for(j=0;   j<point_n-3; j++)
    for(k=j+1; k<point_n-2; k++)
    for(l=k+1; l<point_n-1; l++)
    for(s=l+1; s<point_n;   s++) {
      Block b1 = samples.get(j);
      Block b2 = samples.get(k);
      Block b3 = samples.get(l);
      Block b4 = samples.get(s);

      n1=b1.para; n2=b2.para;
      n3=b3.para; n4=b4.para;

      double xj = b1.tp;
      double xk = b2.tp;
      double xl = b3.tp;
      double xs = b4.tp;

      double Tval=n3*n3/(xl*xl);
      double Sval=n2*n2/(xk*xk);
      double Uval=n4*n4/(xs*xs);
      double Rval=n1*n1/(xj*xj);

      double Kval= (Uval*(n2-n1) - Sval*(n4-n1) + Rval*(n4-n2)) / (Tval*(n2-n1) - Sval*(n3-n1) + Rval*(n3-n2));
      double z =n3*1.0/(n1*1.0);
      double y=n2*1.0/(n1*1.0);
      double w=n4*1.0/(n1*1.0);
      c=0.5;
      double SS,CC;


      for(m=1;m<100;m++) {    
        SS=(n2-n1)*Math.pow(n4,c) - (n4-n1)*Math.pow(n2,c) + (n4-n2)*Math.pow(n1,c) - Kval * ((n2-n1)*Math.pow(n3,c) - (n3-n1)*Math.pow(n2,c) +(n3-n2)*Math.pow(n1,c));
        CC= (n2-n1)*Math.pow(n4,c)*Math.log(n4) -
            (n4-n1)*Math.pow(n2,c)*Math.log(n2) +
            (n4-n2)*Math.pow(n1,c)*Math.log(n1) -
            (n2-n1)*Math.pow(n3,c)*Math.log(n3) -
            (n3-n1)*Math.pow(n2,c)*Math.log(n2) +
            (n3-n2)*Math.pow(n1,c)*Math.log(n1);
        c= c-SS/CC;
      }
      a= (Tval*(n2-n1) - Sval*(n3-n1) + Rval*(n3-n2)) / ((n2-n1)*Math.pow(n3,c) - (n3-n1)*Math.pow(n2,c) + (n3-n2)*Math.pow(n1,c));
      d= (n2*n2/(xk*xk)-(n1*n1)/(xj*xj)+a*Math.pow(n1,c)-a*Math.pow(n2,c))/(n2-n1);
      b=(n1*n1)/(xj*xj) - (d*n1) -a*Math.pow(n1,c);

      double err=0;
      for(m=0;m<point_n;m++) {
        Block mb = samples.get(m);
        int mp = mb.para;
        double temp=mp/Math.sqrt(a*Math.pow(mp,c)+(m+1)*d+b);
        err+=Math.abs(temp-mb.tp);  
      }

      if(err>=0 && err<minerror) {
        optn1=n1; optn2=n2;
        optn3=n3; optn4=n4;

        opta=a; optb=b;
        optc=c; optd=d;
        minerror=err;
      }
    }

    opt_p=1;
    opt_th=0;
    for (int i = 1; i < 100; i++) {
      double th = i/Math.sqrt(opta*Math.pow(i,optc)+i*optd+optb);
      if (th > opt_th) {
        opt_th = th; opt_p = i;
      }
    }

    para = opt_p;
    analysis_done = true;
  }

}
