/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kishida.imagefiltering;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 *
 * @author naoki
 */
public class AutoEncoder {
    public static void main(String[] args) {
        JFrame f = new JFrame("自己符号化器");
        f.setSize(600, 400);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setLayout(new GridLayout(2, 1));
        f.setVisible(true);
        JPanel top = new JPanel(new GridLayout(1, 2));
        f.add(top);
        JLabel left = new JLabel();
        top.add(left);
        JLabel right = new JLabel();
        top.add(right);
        JPanel bottom = new JPanel(new GridLayout(6, 8));
        f.add(bottom);
        JLabel[] labels = IntStream.range(0, 48)
                .mapToObj(i -> new JLabel())
                .peek(bottom::add)
                .toArray(i -> new JLabel[i]);
        
        
        Path dir = Paths.get("C:\\Users\\naoki\\Desktop\\sampleimg");
        new Thread(() -> {
            double[][][][] filters = new double[48][][][];
            for(int i = 0; i < filters.length; ++i){
                filters[i] = new double[][][]{
                    createRandomFilter(11),createRandomFilter(11),createRandomFilter(11)
                };
            }
            for(int i = 0; i < filters.length; ++i){
                labels[i].setIcon(new ImageIcon(resize(arrayToImage(filters[i]), 44, 44)));
            }
            try {
                List<Path> paths = Files.walk(dir).filter(p -> Files.isRegularFile(p)).collect(Collectors.toList());
                Collections.shuffle(paths, r);
                int[] count = {0};
                paths.forEach((Path p) -> {
                    ++count[0];
                    try {
                        System.out.println(count[0] + ":" + p);
                        BufferedImage readImg = ImageIO.read(p.toFile());
                        BufferedImage resized = resize(readImg, 256, 256);
                        left.setIcon(new ImageIcon(resized));
                        double[][][] resizedImage = imageToArray(resized);
                        double[][][] filtered = applyFilter(resizedImage, filters, 1);
                        double[][][] inverseImage = applyInverseFilter(filtered, filters, 1);
                        right.setIcon(new ImageIcon(arrayToImage(inverseImage)));
                        supervisedLearn(inverseImage, resizedImage, filters);
                        for(int i = 0; i < filters.length; ++i){
                            labels[i].setIcon(new ImageIcon(resize(arrayToImage(filters[i]), 44, 44)));
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(AutoEncoder.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
                });
            } catch (IOException ex) {
                Logger.getLogger(AutoEncoder.class.getName()).log(Level.SEVERE, null, ex);
            }
        }).start();
    }
    
    static void supervisedLearn(double[][][] data, double[][][] superviser, double[][][][] filters){
        double[][][][] oldFilters = new double[filters.length][][][];
        IntStream.range(0, filters.length).forEach(i -> {
            oldFilters[i] = new double[filters[i].length][][];
            for(int j = 0; j < filters[i].length; ++j){
                oldFilters[i][j] = new double[filters[i][j].length][];
                for(int k = 0; k < filters[i][j].length; ++k){
                    oldFilters[i][j][k] = new double[filters[i][j][k].length];
                    for(int l = 0; l < filters[i][j][k].length; ++l){
                        oldFilters[i][j][k][l] = filters[i][j][k][l];
                    }
                }
            }
        });
        double delta = 0.0001;
        for(int lch = 0; lch < Math.min(data.length, superviser.length); ++lch){
            int ch = lch;
            int width = Math.min(data[ch].length, superviser[ch].length);
            for(int lx = 0; lx < width; ++lx){
                int x = lx;
                int height = Math.min(data[ch][x].length, superviser[ch][x].length);
                for(int ly = 0; ly < height; ++ly){
                    int y = ly;
                    //for(int f = 0; f < filters.length; ++f){
                    IntStream.range(0, filters.length).parallel().forEach(f -> {
                        for(int i = 0; i < filters[f][ch].length; ++i){
                            int xx = x + i - filters[f][ch].length / 2;
                            if(xx < 0 || xx >= width){
                                continue;
                            }
                            for(int j = 0; j < filters[f][ch][i].length; ++j){
                                int yy = y + i - filters[f][ch][i].length / 2;
                                if(yy < 0 || yy >= height){
                                    continue;
                                }
                                double c1 = superviser[ch][xx][yy];
                                double c2 = data[ch][xx][yy];
                                if(c1 < -1) c1 = -1; else if (c1 > 1) c1 = 1;
                                if(c2 < -1) c2 = -1; else if (c2 > 1) c2 = 1;
                                double d = (c1 - c2) * oldFilters[f][ch][i][j];
                                filters[f][ch][i][j] -= d * delta;
                            }
                        }
                    });
                    //}
                }
            }
        }
    }
    

    private static BufferedImage resize(BufferedImage imgRead, int width, int height) {
        if(imgRead.getWidth() * height > imgRead.getHeight() * width){
            height = imgRead.getHeight() * width / imgRead.getWidth();
        }else{
            width = imgRead.getWidth() * height / imgRead.getHeight();
        }
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.getGraphics();
        ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(imgRead, 0, 0, width, height, null);
        g.dispose();
        return img;
    }    
    
    /** 画像から配列へ変換 */
    private static double[][][] imageToArray(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        double[][][] imageData = new double[3][width][height];
        for(int x = 0; x < width; ++x){
            for(int y = 0; y < height; ++y){
                int rgb = img.getRGB(x, y);
                imageData[0][x][y] = (rgb >> 16 & 0xff) / 128. - 1;
                imageData[1][x][y] = (rgb >> 8 & 0xff) / 128. - 1;
                imageData[2][x][y] = (rgb & 0xff) / 128. - 1;
            }
        }
        return imageData;
    }    
    
    static Random r = new Random();
    static double[][] createRandomFilter(int size){
        double [][] result = new double[size][size];
        double total = 0;
        for(int i = 0; i < size; ++i){
            for(int j = 0; j < size; ++j){
                result[i][j] = (r.nextDouble() - 0.5) * 2;
                total += result[i][j];
            }
        }

        double ave = (total - 1) / (size * size);
        for(int i = 0; i < size; ++i){
            for(int j = 0; j < size; ++j){
                result[i][j] -= ave;
            }
        }
        
        return result;
    }
    
    /** 値のクリッピング */
    static int clip(double c){
        if(c < 0) return 0;
        if(c > 255) return 255;
        return (int)c;
    }

    /** フィルタを適用する */
    static double[][][] applyFilter(double[][][] img, double[][][][] filter, int inStride) {
        int width = img[0].length;
        int height = img[0][0].length;
        int filterSize = filter[0][0].length;
        double[][][] result = new double[filter.length][width / inStride][height / inStride];
        for(int lx = 0; lx < width / inStride; ++lx){
            int x = lx;
            for(int ly = 0; ly < height / inStride; ++ly){
                int y = ly;
                for(int li = 0; li < filter[0][0].length; ++li){
                    int i = li;
                    int xx = x * inStride + i - filterSize / 2;
                    if(xx < 0 || xx >= width){
                        continue;
                    }
                    IntStream.range(0, filter[0][0][0].length).parallel().forEach(j -> {
                    //for(int j = 0; j < filter[0][0][0].length; ++j){
                        int yy = y * inStride + j - filterSize / 2;
                        if(yy < 0 || yy >= height){
                            return;//ループを続ける
                        }
                        for(int fi = 0; fi < filter.length; ++fi){
                            for(int fj = 0; fj < filter[fi].length; ++fj){
                                try{
                                result[fi][x][y] += img[fj][xx][yy] * 
                                        filter[fi][fj][i][j];
                                }catch(ArrayIndexOutOfBoundsException ex){
                                    System.out.println(ex);
                                }
                            }
                        }
                    //}
                    });
                }
            }
        }
        return result;
    }
    /** フィルタを適用する */
    static double[][][] applyInverseFilter(double[][][] img, double[][][][] filter, int outStride) {
        int width = img[0].length;
        int height = img[0][0].length;
        double[][][] result = new double[filter[0].length][width * outStride][height * outStride];
        int filterSize = filter[0][0].length;
        for(int lx = 0; lx < width; ++lx){
            int x = lx;
            for(int ly = 0; ly < height; ++ly){
                int y = ly;
                for(int li = 0; li < filter[0][0].length; ++li){
                    int i = li;
                    int xx = x * outStride + i - filterSize / 2;
                    if(xx < 0 || xx >= width * outStride){
                        continue;
                    }
                        
                    //for(int j = 0; j < filter[0][0][0].length; ++j){
                    IntStream.range(0, filter[0][0][0].length).parallel().forEach(j -> {
                        int yy = y * outStride + j - filterSize / 2;
                        if(yy < 0 || yy >= height * outStride){
                            return; // ループを続ける
                        }
                        for(int fi = 0; fi < filter.length; ++fi){
                            for(int fj = 0; fj < filter[fi].length; ++fj){
                                result[fj][xx][yy] += img[fj][x][y] * 
                                        filter[fi][fj][i][j];
                            }
                        }
                    });
                }
            }
        }
        return result;
    }
        
    
    static BufferedImage arrayToImage(double[][][] filteredData) {
        BufferedImage filtered = new BufferedImage(
                filteredData[0].length, filteredData[0][0].length,
                BufferedImage.TYPE_INT_RGB);
        for(int x = 0; x < filteredData[0].length; ++x){
            for(int y = 0; y < filteredData[0][0].length; ++y){
                filtered.setRGB(x, y,
                        ((int)clip(filteredData[0][x][y] * 255 + 128) << 16) +
                        ((int)clip(filteredData[1][x][y] * 255 + 128) << 8) +
                         (int)clip(filteredData[2][x][y] * 255 + 128));
            }
        }
        return filtered;
    }        
}
