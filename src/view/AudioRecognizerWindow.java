package view;

import model.Complex;
import model.DataPoint;
import model.FFT;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioRecognizerWindow extends JFrame {
    private Map<Long, List<DataPoint>>  hashMap = new HashMap<>();
    private Map<Integer, Map<Integer, Integer>> matchMap; // Map<SongId, Map<Offset, Count>>
    private long nrSong = 0;

    private final int UPPER_LIMIT = 300;
    private final int LOWER_LIMIT = 40;
    private final int[] RANGE = new int[]{40, 80, 120, 180, UPPER_LIMIT + 1};
    private static final int FUZ_FACTOR = 2;

    private void makeSpectrum(byte audio[], long songId, boolean isMatching) throws IOException {
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



    private void determineKeyPoints(Complex[][] results, long songId, boolean isMatching) throws IOException {
        this.matchMap = new HashMap<>();

        FileWriter fstream = new FileWriter("result.txt");

        BufferedWriter outFile = new BufferedWriter(fstream);

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
                // Get the magnitude:
                double mag = Math.log(results[t][freq].abs() + 1);

                // Find out which range we are in:
                int index = getIndex(freq);

                // Save the highest magnitude and corresponding frequency:
                if (mag > highscores[t][index]) {
                    highscores[t][index] = mag;
                    recordPoints[t][freq] = 1;
                    points[t][index] = freq;
                }
            }

            try {
                for (int k = 0; k < 5; k++) {
                    outFile.write("" + highscores[t][k] + ";"
                            + recordPoints[t][k] + "\t");
                }
                outFile.write("\n");

            } catch (IOException e) {
                e.printStackTrace();
            }

            long h = hash(points[t][0], points[t][1], points[t][2],
                    points[t][3]);

            if (isMatching) {
                List<DataPoint> listPoints;

                if ((listPoints = hashMap.get(h)) != null) {
                    for (DataPoint dP : listPoints) {
                        int offset = Math.abs(dP.getTime() - t);
                        Map<Integer, Integer> tmpMap = null;
                        if ((tmpMap = this.matchMap.get(dP.getSongId())) == null) {
                            tmpMap = new HashMap<Integer, Integer>();
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
                List<DataPoint> listPoints = null;
                if ((listPoints = hashMap.get(h)) == null) {
                    listPoints = new ArrayList<>();
                    DataPoint point = new DataPoint((int) songId, t);
                    listPoints.add(point);
                    hashMap.put(h, listPoints);
                } else {
                    DataPoint point = new DataPoint((int) songId, t);
                    listPoints.add(point);
                }
            }
        }
        try {
            outFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Find out in which range
    private int getIndex(int freq) {
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
        Button buttonStart = new Button("Start");
        Button buttonMatch = new Button("Match");
        Button buttonStartMatch = new Button("Start Match");
        JTextField fileTextField = new JTextField(20);
        fileTextField.setText("/home/wiktor/audio/billy.mp3");

        buttonStart.addActionListener(e -> {
            try {
                byte[] audio = Files.readAllBytes(Paths.get(fileTextField.getText()));
                makeSpectrum(audio, nrSong, false);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            nrSong++;
        });

        buttonStartMatch.addActionListener(e -> {
            try {
                byte[] audio = Files.readAllBytes(Paths.get(fileTextField.getText()));
                makeSpectrum(audio, nrSong, true);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        });

        buttonMatch.addActionListener(e -> {
            int bestCount = 0;
            int bestSong = -1;

            for (int id = 0; id < nrSong; id++) {
                Map<Integer, Integer> tmpMap = matchMap.get(id);
                int bestCountForSong = 0;

                for (Map.Entry<Integer, Integer> entry : tmpMap.entrySet()) {
                    if (entry.getValue() > bestCountForSong) {
                        bestCountForSong = entry.getValue();
                    }
                    System.out.println("Time offset = " + entry.getKey()
                            + ", Count = " + entry.getValue());
                }

                if (bestCountForSong > bestCount) {
                    bestCount = bestCountForSong;
                    bestSong = id;
                }
            }

            System.out.println("Best song id: " + bestSong);
        });

        this.add(buttonStart);
        this.add(buttonStartMatch);
        this.add(buttonMatch);
        this.add(fileTextField);
        this.setLayout(new FlowLayout());
        this.setSize(300, 100);
        this.setVisible(true);
    }


    private AudioRecognizerWindow(String windowName) {
        super(windowName);
    }

    public static void main(String[] args)   {
        AudioRecognizerWindow audioWindow = new AudioRecognizerWindow("Audio Recognizer");
        audioWindow.createWindow();
    }

}
