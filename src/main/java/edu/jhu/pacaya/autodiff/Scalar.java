package edu.jhu.pacaya.autodiff;

import edu.jhu.pacaya.autodiff.erma.MVecFgModel;
import edu.jhu.pacaya.util.semiring.Algebra;
import edu.jhu.pacaya.util.semiring.Algebras;

// TODO: Remove this class and always use a 1D Tensor instead.
public class Scalar implements MVec {

    private Algebra s;
    private double value;
    
    public Scalar(double value) {
        this.value = value;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public void fill(double val) {
        value = val;
    }

    @Override
    public Scalar copyAndFill(double val) {
        return new Scalar(val);
    }

    @Override
    public Scalar copyAndConvertAlgebra(Algebra newS) {
        return new Scalar(Algebras.convertAlgebra(value, this.s, newS));
    }

    @Override    
    public void elemAdd(MVec addend) {
        if (addend instanceof Scalar) {
            elemAdd((Scalar)addend);
        } else {
            throw new IllegalArgumentException("Addend must be of type " + this.getClass());
        }
    }
    
    public void elemAdd(Scalar addend) {
        value = s.plus(value, addend.value);
    }

    @Override
    public double getValue(int idx) {
        if (idx != 0) {
            throw new IllegalArgumentException("Invalid index for scalar: " + idx);
        }
        return value;
    }

    @Override
    public double setValue(int idx, double val) {
        if (idx != 0) {
            throw new IllegalArgumentException("Invalid index for scalar: " + idx);
        }
        double tmp = value;
        value = val;
        return tmp;
    }

    @Override
    public Scalar copy() {
        return new Scalar(this.value);
    }

    @Override
    public Algebra getAlgebra() {
        return s;
    }

}
