/*
 *       This file was written before 11:00 20th November.
 * */

package com.example.waste;

public class LabeledObject {
    public String label;
    public float score;
    public LabeledObject(String lb, float sc) {
        label = lb;
        score = sc;
    }
    public LabeledObject() {
        label = null;
        score = 0;
    }
}
