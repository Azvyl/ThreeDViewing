package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renderer teks vektor ringan berbasis GL_LINES untuk menampilkan HUD status
 * langsung di dalam viewport OpenGL tanpa ketergantungan file font eksternal.
 */
public class VectorFontRenderer {

    public static void drawString(String text, float x, float y, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String upper = text.toUpperCase();
        float cursorX = x;
        float charWidth = 6.0f * scale;
        float charHeight = 9.0f * scale;

        glLineWidth(1.35f);
        glBegin(GL_LINES);
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            drawCharSegments(c, cursorX, y, charWidth * 0.72f, charHeight);
            cursorX += charWidth;
        }
        glEnd();
        glLineWidth(1.0f);
    }

    private static void drawCharSegments(char c, float x, float y, float w, float h) {
        float xm = x + w * 0.5f;
        float xr = x + w;
        float ym = y + h * 0.5f;
        float yb = y + h;

        switch (c) {
            case 'A':
                line(x, yb, xm, y); line(xm, y, xr, yb); line(x + w * 0.18f, ym + 1, xr - w * 0.18f, ym + 1);
                break;
            case 'B':
                line(x, y, x, yb); line(x, y, xr * 0.99f - w * 0.15f, y); line(xr - w * 0.15f, y, xr, y + h * 0.25f);
                line(xr, y + h * 0.25f, x, ym); line(x, ym, xr, ym + h * 0.25f); line(xr, ym + h * 0.25f, xr - w * 0.15f, yb);
                line(xr - w * 0.15f, yb, x, yb);
                break;
            case 'C':
                line(xr, y, x, y); line(x, y, x, yb); line(x, yb, xr, yb);
                break;
            case 'D':
                line(x, y, x, yb); line(x, y, xr - w * 0.25f, y); line(xr - w * 0.25f, y, xr, ym);
                line(xr, ym, xr - w * 0.25f, yb); line(xr - w * 0.25f, yb, x, yb);
                break;
            case 'E':
                line(xr, y, x, y); line(x, y, x, yb); line(x, ym, xr - w * 0.2f, ym); line(x, yb, xr, yb);
                break;
            case 'F':
                line(xr, y, x, y); line(x, y, x, yb); line(x, ym, xr - w * 0.2f, ym);
                break;
            case 'G':
                line(xr, y, x, y); line(x, y, x, yb); line(x, yb, xr, yb); line(xr, yb, xr, ym); line(xm, ym, xr, ym);
                break;
            case 'H':
                line(x, y, x, yb); line(xr, y, xr, yb); line(x, ym, xr, ym);
                break;
            case 'I':
                line(x, y, xr, y); line(xm, y, xm, yb); line(x, yb, xr, yb);
                break;
            case 'J':
                line(xr, y, xr, yb); line(xr, yb, x, yb); line(x, yb, x, ym + 1);
                break;
            case 'K':
                line(x, y, x, yb); line(xr, y, x, ym); line(x, ym, xr, yb);
                break;
            case 'L':
                line(x, y, x, yb); line(x, yb, xr, yb);
                break;
            case 'M':
                line(x, yb, x, y); line(x, y, xm, ym); line(xm, ym, xr, y); line(xr, y, xr, yb);
                break;
            case 'N':
                line(x, yb, x, y); line(x, y, xr, yb); line(xr, yb, xr, y);
                break;
            case 'O':
            case '0':
                line(x, y, xr, y); line(xr, y, xr, yb); line(xr, yb, x, yb); line(x, yb, x, y);
                if (c == '0') { line(x, yb, xr, y); }
                break;
            case 'P':
                line(x, yb, x, y); line(x, y, xr, y); line(xr, y, xr, ym); line(xr, ym, x, ym);
                break;
            case 'Q':
                line(x, y, xr, y); line(xr, y, xr, yb); line(xr, yb, x, yb); line(x, yb, x, y); line(xm, ym, xr, yb);
                break;
            case 'R':
                line(x, yb, x, y); line(x, y, xr, y); line(xr, y, xr, ym); line(xr, ym, x, ym); line(xm, ym, xr, yb);
                break;
            case 'S':
            case '5':
                line(xr, y, x, y); line(x, y, x, ym); line(x, ym, xr, ym); line(xr, ym, xr, yb); line(xr, yb, x, yb);
                break;
            case 'T':
                line(x, y, xr, y); line(xm, y, xm, yb);
                break;
            case 'U':
                line(x, y, x, yb); line(x, yb, xr, yb); line(xr, yb, xr, y);
                break;
            case 'V':
                line(x, y, xm, yb); line(xm, yb, xr, y);
                break;
            case 'W':
                line(x, y, x + w * 0.25f, yb); line(x + w * 0.25f, yb, xm, ym);
                line(xm, ym, xr - w * 0.25f, yb); line(xr - w * 0.25f, yb, xr, y);
                break;
            case 'X':
                line(x, y, xr, yb); line(xr, y, x, yb);
                break;
            case 'Y':
                line(x, y, xm, ym); line(xr, y, xm, ym); line(xm, ym, xm, yb);
                break;
            case 'Z':
            case '2':
                line(x, y, xr, y); line(xr, y, x, yb); line(x, yb, xr, yb);
                break;
            case '1':
                line(xm, y, xm, yb); line(x + w * 0.2f, y + h * 0.2f, xm, y); line(x, yb, xr, yb);
                break;
            case '3':
                line(x, y, xr, y); line(xr, y, xr, yb); line(x, ym, xr, ym); line(x, yb, xr, yb);
                break;
            case '4':
                line(x, y, x, ym); line(x, ym, xr, ym); line(xr, y, xr, yb);
                break;
            case '6':
                line(xr, y, x, y); line(x, y, x, yb); line(x, yb, xr, yb); line(xr, yb, xr, ym); line(xr, ym, x, ym);
                break;
            case '7':
                line(x, y, xr, y); line(xr, y, xm, yb);
                break;
            case '8':
                line(x, y, xr, y); line(xr, y, xr, yb); line(xr, yb, x, yb); line(x, yb, x, y); line(x, ym, xr, ym);
                break;
            case '9':
                line(xr, ym, x, ym); line(x, ym, x, y); line(x, y, xr, y); line(xr, y, xr, yb); line(xr, yb, x, yb);
                break;
            case ':':
                line(xm, y + h * 0.25f, xm, y + h * 0.35f); line(xm, y + h * 0.65f, xm, y + h * 0.75f);
                break;
            case '.':
                line(xm, yb - 1.5f, xm, yb);
                break;
            case ',':
                line(xm, yb - 1.5f, xm - 1.2f, yb + 1.8f);
                break;
            case '-':
                line(x, ym, xr, ym);
                break;
            case '+':
                line(x, ym, xr, ym); line(xm, y + h * 0.25f, xm, y + h * 0.75f);
                break;
            case '=':
                line(x, ym - 1.8f, xr, ym - 1.8f); line(x, ym + 1.8f, xr, ym + 1.8f);
                break;
            case '/':
                line(x, yb, xr, y);
                break;
            case '%':
                line(x, yb, xr, y); line(x, y, x + 1.5f, y + 1.5f); line(xr - 1.5f, yb - 1.5f, xr, yb);
                break;
            case '&':
                line(xr, ym, x, y + h * 0.2f); line(x, y + h * 0.2f, xm, y); line(xm, y, x, yb); line(x, yb, xr, ym);
                break;
            case '|':
                line(xm, y, xm, yb);
                break;
            case '[':
            case '(':
                line(xr - w * 0.2f, y, x + w * 0.2f, y); line(x + w * 0.2f, y, x + w * 0.2f, yb);
                line(x + w * 0.2f, yb, xr - w * 0.2f, yb);
                break;
            case ']':
            case ')':
                line(x + w * 0.2f, y, xr - w * 0.2f, y); line(xr - w * 0.2f, y, xr - w * 0.2f, yb);
                line(xr - w * 0.2f, yb, x + w * 0.2f, yb);
                break;
            case '_':
                line(x, yb, xr, yb);
                break;
            default:
                break;
        }
    }

    private static void line(float x1, float y1, float x2, float y2) {
        glVertex2f(x1, y1);
        glVertex2f(x2, y2);
    }
}
