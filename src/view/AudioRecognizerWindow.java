package view;

import model.Complex;
import model.FFT;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class AudioRecognizerWindow extends JFrame {
    private Map<Long, List<DataPoint>> registerMap = new HashMap<>();
    private Map<Integer, Map<Integer, Integer>> matchMap; // Map<SongId, Map<Offset, Count>>
    private int nrSong = 0;

    private final int UPPER_LIMIT = 300;
    private final int LOWER_LIMIT = 40;
    private final int[] RANGE = new int[]{40, 80, 120, 180, UPPER_LIMIT + 1};
    private static final int FUZ_FACTOR = 2;

    private void makeSpectrum(byte audio[], int songId, boolean isMatching)  {
        final int totalSize = audio.length;

        int amountPossible = totalSize / 4096;

        // When turning into frequency domain we'll need complex numbers:
        Complex[][] results = new Complex[amountPossible][];

        // For all the chunks:
        for (int times = 0; times < amountPossible; times++) {
            Complex[] complex = new Complex[4096];
            for (int i = 0; i < 4096; i++) {
                // Put the time domain data into a complex number with imaginary
                // part as 0:
                complex[i] = new Complex(audio[(times * 4096) + i], 0);
            }
            // Perform FFT analysis on the chunk:
            results[times] = FFT.fft(complex);
        }
        determineKeyPoints(results, songId, isMatching);
    }



    private void determineKeyPoints(Complex[][] results, int songId, boolean isMatching)   {
        this.matchMap = new HashMap<>();

        double[][] highscores = new double[results.length][5];
        for (int i = 0; i < results.length; i++) {
            for (int j = 0; j < 5; j++) {
                highscores[i][j] = 0;
            }
        }

        double[][] recordPoints = new double[results.length][UPPER_LIMIT];
        for (int i = 0; i < results.length; i++) {
            for (int j = 0; j < UPPER_LIMIT; j++) {
                recordPoints[i][j] = 0;
            }
        }

        long[][] points = new long[results.length][5];
        for (int i = 0; i < results.length; i++) {
            for (int j = 0; j < 5; j++) {
                points[i][j] = 0;
            }
        }

        for (int t = 0; t < results.length; t++) {
            for (int freq = LOWER_LIMIT; freq < UPPER_LIMIT - 1; freq++) {
                double magnitude = Math.log(results[t][freq].abs() + 1);
                int index = getRangeIndex(freq);
                // Save the highest magnitude and corresponding frequency:
                if (magnitude > highscores[t][index]) {
                    highscores[t][index] = magnitude;
                    recordPoints[t][freq] = 1;
                    points[t][index] = freq;
                }
            }

            long hashKey = hash(points[t][0], points[t][1], points[t][2], points[t][3]);

            if (isMatching) {
                List<DataPoint> listPoints = registerMap.get(hashKey);
                if (listPoints != null) {
                    for (DataPoint dP : listPoints) {
                        int offset = Math.abs(dP.getTime() - t);
                        Map<Integer, Integer> tmpMap = null;
                        if ((tmpMap = this.matchMap.get(dP.getSongId())) == null) {
                            tmpMap = new HashMap<>();
                            tmpMap.put(offset, 1);
                            matchMap.put(dP.getSongId(), tmpMap);
                        } else {
                            Integer count = tmpMap.get(offset);
                            if (count == null) {
                                tmpMap.put(offset, 1);
                            } else {
                                tmpMap.put(offset, count + 1);
                            }
                        }
                    }
                }
            } else {
                List<DataPoint> listPoints = registerMap.computeIfAbsent(hashKey, k -> new ArrayList<>());
                listPoints.add(new DataPoint(  songId, t));
            }
        }
    }

    private int getRangeIndex(int freq) {
        int i = 0;
        while (RANGE[i] < freq)
            i++;
        return i;
    }

    private long hash(long p1, long p2, long p3, long p4) {
        return (p4 - (p4 % FUZ_FACTOR)) * 100000000 + (p3 - (p3 % FUZ_FACTOR))
                * 100000 + (p2 - (p2 % FUZ_FACTOR)) * 100
                + (p1 - (p1 % FUZ_FACTOR));
    }

    private void createWindow()   {
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Button buttonRegister = new Button("Register");
        Button buttonMatch = new Button("Match");
        JTextField fileTextField = new JTextField(20);
        fileTextField.setText("E:\\Projects-mine2\\playWithsound\\my1.pcm");

        buttonRegister.addActionListener(e -> {
            try {
                byte[] audio = Files.readAllBytes(Paths.get(fileTextField.getText()));
                makeSpectrum(audio, nrSong, false);
                nrSong++;
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        });

        buttonMatch.addActionListener(e -> {
            try {
                byte[] audio = Files.readAllBytes(Paths.get(fileTextField.getText()));
                makeSpectrum(audio, 0, true);
                printMatchMap();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        });

        this.add(buttonRegister);
        this.add(buttonMatch);
        this.add(fileTextField);
        this.setLayout(new FlowLayout());
        this.setSize(300, 100);
        this.setVisible(true);
    }

    private void printMatchMap() {
        int bestCount = 0;
        int bestSongId = -1;

        Set<Integer> keys = matchMap.keySet();
        for (Integer key : keys) {
            Map<Integer, Integer> tmpMap = matchMap.get(key);
            int bestCountForSong = 0;

            for (Map.Entry<Integer, Integer> entry : tmpMap.entrySet()) {
                if (entry.getValue() > bestCountForSong) {
                    bestCountForSong = entry.getValue();
                }
            }

            if (bestCountForSong > bestCount) {
                bestCount = bestCountForSong;
                bestSongId = key;
            }
        }

        System.out.println("Best song id: " + bestSongId);
    }


    private AudioRecognizerWindow(String windowName) {
        super(windowName);
    }

    public static void main(String[] args)   {
        AudioRecognizerWindow audioWindow = new AudioRecognizerWindow("Audio Recognizer");
        audioWindow.createWindow();
    }

}
