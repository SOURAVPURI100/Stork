package stork.optimizers;

import java.util.Scanner;

// A class for doing quadratic regression on a dataset.
//
// This can be done by solving for a = (XtX)^-1 * Xt * y, where
// y is the vector of throughputs, X is a matrix whose rows
// correspond to powers of xi where xi is the ith data point, and a
// is the vector of coefficients. XtX boils down to a matrix of
// sums of powers of xi, so we simply store those sums of powers in
// an array T. For more information, see:
//
//   http://en.wikipedia.org/wiki/Polynomial_regression

public class InvQuadRegression {
  private double[] T = new double[4];
  private double[] X, y;
  private int n = 0;

  public InvQuadRegression(int size) {
    X = new double[size];
    y = new double[size];
  }

  public void add(int x1, double y1) {
    if (n >= X.length) return;

    int x2 = x1*x1, x3 = x2*x1, x4 = x3*x1;

    T[0] += 1.0/x1;
    T[1] += 1.0/x2;
    T[2] += 1.0/x3;
    T[3] += 1.0/x4;

    X[n] = x1; y[n++] = y1;
  }

  // Calculate the coefficients, return array whose indices correspond
  // to the powers of x for which the value is the coefficient. Returns
  // null if the coefficients cannot be calculated (usually the result
  // of not enough data points).
  public double[] calculate() {
    if (n < 3) return null;

    double det = detT();

    // Check that matrix is invertible.
    if (det == 0) return null;

    // Inverse XtX
    double[][] Ti = XtXinv(det);
    double[] a = new double[3];
    
    // Sum up [c,b,a]
    for (int i = 0; i < n; i++) {
      a[0] += y[i] * (Ti[0][0] + Ti[0][1]/X[i] + Ti[0][2]/X[i]/X[i]);
      a[1] += y[i] * (Ti[1][0] + Ti[1][1]/X[i] + Ti[1][2]/X[i]/X[i]);
      a[2] += y[i] * (Ti[2][0] + Ti[2][1]/X[i] + Ti[2][2]/X[i]/X[i]);
    }

    return a;
  }

  // Modular view of the XtX matrix.
  private double XtX(int i, int j) {
    int x = (i%3)+(j%3);
    return (x == 0) ? n : T[x-1];
  }

  // Get the value of det(XtX)*(XtX)^-1 in a cool way. You'll have
  // to calculate and divide out the det(XtX) yourself.
  private double detXtXinv(int i, int j) {
    return XtX(i+1, j+1)*XtX(i+2, j+2) -
           XtX(i+1, j+2)*XtX(i+2, j+1);
  }

  // Now get det(XtX).
  private double detT() {
    return n*(T[1]*T[3]-T[2]*T[2])-
        T[0]*(T[3]*T[0]-T[2]*T[1])+
        T[1]*(T[0]*T[2]-T[1]*T[1]);
  }

  // Finally, generate (XtX)^-1.
  private double[][] XtXinv(double det) {
    double[][] M = new double[3][3];

    M[0][0] = detXtXinv(0,0) / det;
    M[0][1] = detXtXinv(0,1) / det;
    M[0][2] = detXtXinv(0,2) / det;

    M[1][0] = detXtXinv(1,0) / det;
    M[1][1] = detXtXinv(1,1) / det;
    M[1][2] = detXtXinv(1,2) / det;

    M[2][0] = detXtXinv(2,0) / det;
    M[2][1] = detXtXinv(2,1) / det;
    M[2][2] = detXtXinv(2,2) / det;

    return M;
  }

  static class Pair {
    int sc;
    double th;

    Pair(int sc, double th) {
      this.sc = sc;
      this.th = th;
    }
  }

  public static double cal_thr(double[] a, int n) {
    return n/Math.sqrt(n*n*a[0]+n*a[1]+a[2]);
  }

  public static void main(String[] args) {
    Scanner s = new Scanner(System.in);
    int num = 500000;

    // Test values
    double[] test_a = { 30, -40, 36 };

    //InvQuadRegression qr = new InvQuadRegression(list.size());
    InvQuadRegression qr = new InvQuadRegression(num);

    // Seed qr with random stuff.
    for (int i = 0; i < num; i++) {
      int n = (int) (15*Math.random())+1;
      double th = cal_thr(test_a, n);// + .1*Math.random();
      qr.add(n, 1/(th*th));
    }

    double[] a = qr.calculate();
  }
}
