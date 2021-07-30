package agents;

public class Thresholds {
    public static int[] thresholds;

    public Thresholds(int[] args) {
        thresholds = args;
    }

    public static int[] getThresholds() {
        return thresholds;
    }

    public static void setThresholds(int[] thresholds) {
        Thresholds.thresholds = thresholds;
    }

    public static void adjustThresholds(int justification) {
        // Iterate over each objective and subtract the threshold
        for (int i = 0; i < thresholds.length; i++) {
            if (i == justification) {
                // for the faulted objective, increase the threshold by 10% and round to int
                thresholds[i] = (int) (thresholds[i]*1.1);
            } else {
                // for all other objectives, decrease the threshold by 10% and round to int
                thresholds[i] = (int) (thresholds[i]*0.9);
            }
        }
    }

}
