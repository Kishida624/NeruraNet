/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kishida.cnn.layers;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.stream.IntStream;
import kishida.cnn.ConvolutionalNet;
import kishida.cnn.activation.ActivationFunction;
import kishida.cnn.kernels.FullyForwardKernel;
import kishida.cnn.util.FloatUtil;

/**
 *
 * @author naoki
 */
public class FullyConnect extends NeuralLayer implements LerningLayer{
    float[]weight;
    float[] bias;
    float[]weightDelta;
    float[] biasDelta;
    int out;
    int in;
    int[] dropout;
    float dropoutRate = 1;
    float learningRate;
    boolean useGpu;
	float[] newDelta;
	float[] diffed;

    public FullyConnect(String name, NeuralLayer preLayer, int out, float initBias, float dropoutRate, ActivationFunction activation, float learningRate, boolean useGpu) {
        this(name, preLayer.getOutputSize(), out,initBias, dropoutRate, activation, learningRate, useGpu);
        this.preLayer = preLayer;
    }

    public FullyConnect(String name, int in, int out, float initBias, float dropoutRate, ActivationFunction activation, float learningRate, boolean useGpu) {
        this(name, in, out,
                FloatUtil.createGaussianArray(in * out, 0.01f, ConvolutionalNet.random),
                FloatUtil.createArray(out, initBias),
                dropoutRate, learningRate, activation, useGpu);
    }

    public FullyConnect(String name, int in, int out, float[] weight,
            float[] bias, float dropoutRate, float learningRate, ActivationFunction activation, boolean useGpu) {
        super(name, activation);
        this.name = name;
        this.in = in;
        this.out = out;
        this.weight = weight;
        this.bias = bias;
        this.weightDelta = new float[in * out];
        this.biasDelta = new float[out];
        this.learningRate = learningRate;
        this.dropout = IntStream.generate(() -> 1).limit(out).toArray();
        this.dropoutRate = dropoutRate;
        this.weight = weight;
        this.bias = bias;
        this.useGpu = useGpu;
        this.newDelta = new float[in];
        this.diffed = new float[out];
    }

    public void prepareDropout() {
        dropout = ConvolutionalNet.random.doubles(out).mapToInt(d -> d < dropoutRate ? 1 : 0).toArray();
    }

    @Override
    public float[] forward(float[] in) {
        prepareDropout();
        result = new float[out];

        FullyForwardKernel.INSTANCE.forward(out, dropout, in, result, weight, bias, useGpu);
        /*
        IntStream.range(0, out).parallel().filter(j -> dropout[j] == 1).forEach(j -> {
            for (int i = 0; i < in.length; ++i) {
                result[j] += in[i] * weight[i * out + j];
            }
            result[j] += bias[j];
        });*/
        activation.applyAfter(result);
        return result;
    }

    @Override
    public float[] backward(float[] in, float[] delta) {
        Arrays.fill(newDelta, 0);
		Arrays.fill(diffed, 0);
        for(int i = 0; i < result.length; ++i){
                diffed[i] = activation.diff(result[i]);
        }
        IntStream.range(0, in.length).parallel().forEach((i) -> {
            for (int j = 0; j < out; ++j) {
                if (dropout[j] != 1) {
                    continue;
                }
                float d = diffed[j] * delta[j];
                newDelta[i] += d *  weight[i * out + j];//in[i] *;
                weightDelta[i * out + j] += d * in[i] * learningRate;
            }
        });
        IntStream.range(0, out).parallel().filter(j -> dropout[j] == 1).forEach(j -> {
            biasDelta[j] += diffed[j] * delta[j] * learningRate;
        });
        return newDelta;
    }

    @Override
    public void prepareBatch(float momentam) {
        IntStream.range(0, weightDelta.length).forEach(i -> weightDelta[i] = weightDelta[i] * momentam);
        IntStream.range(0, biasDelta.length).parallel().forEach(i -> biasDelta[i] = biasDelta[i] * momentam);
    }

    @Override
    public void joinBatch(int count, float weightDecay, float learningRate) {
        IntStream.range(0, weight.length).parallel().forEach(ij -> {
                weight[ij] += weightDelta[ij] / count
                        - weight[ij] * weightDecay * learningRate;
        });
        IntStream.range(0, bias.length).parallel().forEach(i -> {
            bias[i] += biasDelta[i] / count;
        });
    }

    @Override
    public int getOutputSize() {
        return out;
    }

    public float[] getBias() {
        return bias;
    }

    @Override
    public String toString() {
        return String.format("%s:Fully connect %d->%d dropout:%.2f", name, in, out, dropoutRate);
    }

    @Override
    public DoubleSummaryStatistics getWeightStatistics() {
        return FloatUtil.summary(weight);
    }

    @Override
    public DoubleSummaryStatistics getBiasStatistics() {
        return FloatUtil.summary(bias);
    }

}
