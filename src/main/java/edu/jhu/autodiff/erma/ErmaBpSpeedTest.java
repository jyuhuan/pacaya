package edu.jhu.autodiff.erma;

import org.apache.commons.math3.util.FastMath;

import edu.jhu.autodiff.erma.ErmaBp.ErmaBpPrm;
import edu.jhu.gm.inf.BeliefPropagation.BpScheduleType;
import edu.jhu.gm.inf.BeliefPropagation.BpUpdateOrder;
import edu.jhu.gm.model.ExplicitFactor;
import edu.jhu.gm.model.FactorGraph;
import edu.jhu.gm.model.Var;
import edu.jhu.gm.model.Var.VarType;
import edu.jhu.gm.model.VarSet;
import edu.jhu.util.Prng;
import edu.jhu.util.Timer;
import edu.jhu.util.semiring.Algebras;

public class ErmaBpSpeedTest {

    // Number of tokens. 23 is avg sent length in sec 22 of PTB.
    private static final int n = 23;
    
    // @Test
    public void run() {
        Prng.seed(1234567134);
        for (int round=0; round<2; round++) {
            Timer timer = new Timer();
            for (int t=0; t<10000; t++) {
                timer.start();
                FactorGraph fg = getFg();
                runBp(fg, t);
                long elapsed = timer.elapsedSinceLastStart();
                timer.stop();
                double sentsPerSec = ((double) t) / timer.totSec();
                double toksPerSec = sentsPerSec * n;
                if (t%1000==0) {
                    System.out.println(String.format("r=%d t=%5d avg(ms)=%10.3f tot(ms)=%10.3f tok/sec=%10.3f", round, t,
                        timer.avgMs(), timer.totSec(), toksPerSec));
                }
            }
        }
    }
    
    public static void runBp(FactorGraph fg, int t) {
        ErmaBpPrm prm = new ErmaBpPrm();
//        if (t % 2 == 0) {
//            prm.s = Algebras.REAL_ALGEBRA;
//        } else {
//            prm.s = Algebras.LOG_SEMIRING;
//        }
        //prm.s = Algebras.LOG_SIGN_ALGEBRA;
        prm.s = Algebras.REAL_ALGEBRA;
        prm.maxIterations = 1;
        prm.updateOrder = BpUpdateOrder.SEQUENTIAL;
        prm.schedule = BpScheduleType.TREE_LIKE;
        ErmaBp bp = new ErmaBp(fg, prm);
        bp.run();
    }

    public static FactorGraph getFg() {
        FactorGraph fg = new FactorGraph();
        Var[] vs = new Var[n];
        for (int i=0; i<n; i++) {
            vs[i] = new Var(VarType.PREDICTED, 10, "t"+i, null);
            // Add a unary factor.
            ExplicitFactor f1 = new ExplicitFactor(new VarSet(vs[i]));
            randomInit(f1);
            fg.addFactor( f1 );
            // Add a transition factor.
            if (i > 0) {
                ExplicitFactor f2 = new ExplicitFactor(new VarSet(vs[i], vs[i-1]));
                randomInit(f2);
                fg.addFactor( f2 );
            }
        }
        return fg;
    }

    private static void randomInit(ExplicitFactor f1) {
        for (int c=0; c<f1.size(); c++) {
            f1.setValue(c, Prng.nextDouble());
        }
    }

    private static double exp(double x) {
        x = 1d + x / 256d;
        x *= x;
        x *= x;
        x *= x;
        x *= x;
        x *= x;
        x *= x;
        x *= x;
        x *= x;
        return x;
    }

    public static void main(String[] args) {
        (new ErmaBpSpeedTest()).run();
    }
    
}
