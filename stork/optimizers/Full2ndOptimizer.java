package stork.optimizers;

import stork.ad.*;
import stork.util.*;
import java.util.*;

// Full 2nd order optimizer. Sampling starts at the parallelism
// minimum, and increases by powers of two (relative to the min).
// The sampling stops when throughput is found to have decreased
// sampling stops once the max parallelism is reached, taking a
// final sample at the maximum. Then an analysis is done, and a
// function describing the throughput relative to parallelism
// is produced and used to choose the best parallelism for the
// transfer.

public class Full2ndOptimizer extends Optimizer {
  List<Block> samples = new ArrayList<Block>();
  boolean warmed_up = false;
  boolean done_sampling = false, analysis_done = false;
  double last_tp = 0;
  int p_base = 0, pd = 1, para;
  long off = 0, size;
  Range p_range;

  // Little thing to hold sampling results in a nicer way.
  private class Block {
    int para = Full2ndOptimizer.this.para;
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
    return "full_2nd";
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

    long sample = (long) ((size >= 5E8) ? 5E7 : size/10.0);

    // Don't transfer more than what's available.
    if (off+sample >= size)
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
    } else if (!done_sampling) {
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

  // Will return negative if result should be ignored.
  double cal_err(double a, double b, double c) {
    double sqr_sum = 0.0;
    int n = samples.size();
    int df = 1;

    // Sum squares of differences between predicted and actual throughput
    for (Block bl : samples) {
      double thr = cal_thr(a, b, c, bl.para);
      if (thr <= 0)
        df = -1;
      else
        sqr_sum += (bl.tp-thr)*(bl.tp-thr);
    }

    return df * Math.sqrt(sqr_sum/n);
  }

  // Calculate the difference of two terms n2^2/thn2^2 n1^2/thn1^2 given
  // two samples.
  static double cal_dif(Block s1, Block s2) {
    int n1 = s1.para, n2 = s2.para;
    return (n1*n1/s1.tp/s1.tp - n2*n2/s2.tp/s2.tp) / (n1-n2);
  }

  static double cal_a(Block i, Block j, Block k) {
    return (cal_dif(k, i) - cal_dif(j, i)) / (k.para - j.para);
  }

  static double cal_b(Block i, Block j, Block k, double a) {
    return cal_dif(j, i) - (i.para + j.para) * a;
  }

  static double cal_c(Block i, Block j, Block k, double a, double b) {
    int ni = i.para;
    return ni*ni/i.tp/i.tp - ni*ni*a - ni*b;
  }

  // Calculate the throughput of n streams as predicted by our model.
  static double cal_thr(double a, double b, double c, int n) {
    if (a*n*n + b*n + c <= 0) return 0;
    return n / Math.sqrt(a*n*n + b*n + c);
  }

  // Calculate the optimal stream count based on the prediction model.
  static int cal_full_peak2(double a, double b, double c, Range r) {
    int n = r.min();
    double thr = cal_thr(a, b, c, 1);

    for (int i : r) {
      double t = cal_thr(a, b, c, i);
      if (t < thr) return n;
      thr = t; n = i;
    } return r.max();
  }

  // Calculate the optimal stream count based on derivative.
  static int cal_full_peak(double a, double b, double c, Range r) {
    int n = (int) Math.round(-2*c/b);
    
    return (n > r.max()) ? r.max() :
           (n < r.min()) ? r.min() : n;
  }

  // Perform the analysis which sets parallelism. Once the
  // parallelism is set here, nothing else should change it.
  private double doAnalysis2() {
    if (samples.size() < 3) {
      para = bestParallelism();
      return 0;
    }

    int i, j, k, num = samples.size();
    double a = 0, b = 0, c = 0, err = Double.POSITIVE_INFINITY;
    
    // Iterate through the samples, find the "best" three.
    for (i = 0;   i < num-2; i++)
    for (j = i+1; j < num-1; j++)
    for (k = j+1; k < num;   k++) {
      Block bi = samples.get(i);
      Block bj = samples.get(j);
      Block bk = samples.get(k);

      System.out.printf("Samples: %d %d %d\n", bi.para, bj.para, bk.para);
      System.out.printf("         %.2f %.2f %.2f\n", bi.tp, bj.tp, bk.tp);

      double a_ = cal_a(bi, bj, bk),
             b_ = cal_b(bi, bj, bk, a_),
             c_ = cal_c(bi, bj, bk, a_, b_),
             err_ = cal_err(a_, b_, c_);
      System.out.printf("Got: %.2f %.2f %.2f %.2f\n", a_, b_, c_, err_);

      // If new err is better, replace old calculations.
      if (err_ > 0 && err_ < err) {
        err = err_; a = a_; b = b_; c = c_;
      }
    }

    para = cal_full_peak(a, b, c, p_range);
    analysis_done = true;

    System.out.printf("ANALYSIS DONE!! Got: x/sqrt((%f)*x^2+(%f)*x+(%f)) = %d\n", a, b, c, para);
    return err;
  }

  // Perform analysis using inverse quadratic regression.
  private void doAnalysis() {
    if (samples.size() < 3) {
      para = bestParallelism();
      return;
    }

    // For testing, get old method's error first.
    double err1 = doAnalysis2();

    InvQuadRegression iqr = new InvQuadRegression(samples.size());

    // Add transformed samples to quadratic regression.
    for (Block s : samples) {
      int n = s.para;
      double th = s.tp;
      
      if (th < 0) continue;

      iqr.add(n, 1/(th*th));
    }

    // Moment of truth...
    double[] a = iqr.calculate();

    if (a == null) {
      para = bestParallelism();
      return;
    }

    para = cal_full_peak(a[0], a[1], a[2], p_range);
    analysis_done = true;

    // Compare errors.
    double err2 = cal_err(a[0], a[1], a[2]);
  }
}
