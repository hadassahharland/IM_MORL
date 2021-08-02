package agents;

public class Thresholds {
    public static double[] thresholds;

    public Thresholds(double[] args) {
        thresholds = args;
    }

    public static double[] getThresholds() {
        return thresholds;
    }

    public static void setThresholds(double[] thresholds) {
        Thresholds.thresholds = thresholds;
    }

    public static void adjustThresholds(int justification) {
        // Iterate over each objective and subtract the threshold
        for (int i = 0; i < thresholds.length; i++) {
            if (i == justification) {
                // for the faulted objective, increase the threshold by 10%
                thresholds[i] = thresholds[i]*1.1;
            } else {
                // for all other objectives, decrease the threshold by 10%
                thresholds[i] =  thresholds[i]*0.9;
            }
        }
    }

}
