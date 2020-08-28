/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.Random;
import java.util.stream.IntStream;

/**
 *
 * @author WINDOWS_X
 */
public class buatExternalTraceMul {
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Random random = new Random();
        // TODO code application logic here
        for (int i = 0; i < 7752; i++) {
            int h = random.nextInt(400000)+100000;
            System.out.println(h);
        }
    }
    
}
