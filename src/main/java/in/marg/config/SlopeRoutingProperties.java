package in.marg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marg.routing.slope")
public class SlopeRoutingProperties {

    private double freeMaxDegrees = 5.0;
    private double lowMaxDegrees = 10.0;
    private double moderateMaxDegrees = 15.0;
    private double steepMaxDegrees = 25.0;
    private double verySteepMaxDegrees = 30.0;

    private double lowPenalty = 0.15;
    private double moderatePenalty = 0.40;
    private double steepPenalty = 1.00;
    private double verySteepPenalty = 2.00;

    private boolean blockNoData = true;
    private boolean blockAboveVerySteep = true;

    public double getFreeMaxDegrees() {
        return freeMaxDegrees;
    }

    public void setFreeMaxDegrees(double value) {
        freeMaxDegrees = value;
    }

    public double getLowMaxDegrees() {
        return lowMaxDegrees;
    }

    public void setLowMaxDegrees(double value) {
        lowMaxDegrees = value;
    }

    public double getModerateMaxDegrees() {
        return moderateMaxDegrees;
    }

    public void setModerateMaxDegrees(double value) {
        moderateMaxDegrees = value;
    }

    public double getSteepMaxDegrees() {
        return steepMaxDegrees;
    }

    public void setSteepMaxDegrees(double value) {
        steepMaxDegrees = value;
    }

    public double getVerySteepMaxDegrees() {
        return verySteepMaxDegrees;
    }

    public void setVerySteepMaxDegrees(double value) {
        verySteepMaxDegrees = value;
    }

    public double getLowPenalty() {
        return lowPenalty;
    }

    public void setLowPenalty(double value) {
        lowPenalty = value;
    }

    public double getModeratePenalty() {
        return moderatePenalty;
    }

    public void setModeratePenalty(double value) {
        moderatePenalty = value;
    }

    public double getSteepPenalty() {
        return steepPenalty;
    }

    public void setSteepPenalty(double value) {
        steepPenalty = value;
    }

    public double getVerySteepPenalty() {
        return verySteepPenalty;
    }

    public void setVerySteepPenalty(double value) {
        verySteepPenalty = value;
    }

    public boolean isBlockNoData() {
        return blockNoData;
    }

    public void setBlockNoData(boolean value) {
        blockNoData = value;
    }

    public boolean isBlockAboveVerySteep() {
        return blockAboveVerySteep;
    }

    public void setBlockAboveVerySteep(boolean value) {
        blockAboveVerySteep = value;
    }
}