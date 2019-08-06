package eu.xenit.alfresco.instrumentation.repo;

import brave.http.HttpAdapter;
import brave.http.HttpSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class HttpPercentageSampler extends HttpSampler{

    private static final Logger log = LoggerFactory.getLogger(HttpPercentageSampler.class);

    private float percentage = 1.0F;
    private Random random = new Random();

    public HttpPercentageSampler() {
    }

    public float getPercentage() {
        return percentage;
    }

    public void setPercentage(float percentage) {
        if (percentage < 0 || percentage > 1.001)
            throw new IllegalArgumentException("Percentage "+percentage+" must be between 0 and 1");
        log.debug("HttpPercentageSampler with "+percentage);
        this.percentage = percentage;
    }

    @Override
    public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
        boolean result = sampleIt();
        log.debug("Samples ? "+result);
        return result;
    }

    private boolean sampleIt() {
        if (getPercentage() <= 0.0)
            return false;

        if (getPercentage() >= 1.0)
            return true;

        return random.nextInt(101) <= 100*getPercentage();
    }
}
