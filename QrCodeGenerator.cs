using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Text;

namespace AirTouchPC
{
    public static class QrCodeGenerator
    {
        private static int GfMul(int a, int b)
        {
            int res = 0;
            while (b != 0)
            {
                if ((b & 1) != 0) res ^= a;
                a <<= 1;
                if ((a & 0x100) != 0) a ^= 0x11D;
                b >>= 1;
            }
            return res;
        }

        private static int[] RsEncode(int[] data, int ecLen)
        {
            int[] gen = new int[] { 1 };
            int root = 1;
            for (int step = 0; step < ecLen; step++)
            {
                int[] next = new int[gen.Length + 1];
                for (int i = 0; i < gen.Length; i++)
                {
                    next[i] ^= gen[i];
                    next[i + 1] ^= GfMul(gen[i], root);
                }
                gen = next;
                root = GfMul(root, 2);
            }
            int[] msg = new int[data.Length + ecLen];
            Array.Copy(data, msg, data.Length);
            for (int i = 0; i < data.Length; i++)
            {
                int coef = msg[i];
                if (coef != 0)
                {
                    for (int j = 1; j < gen.Length; j++) msg[i + j] ^= GfMul(gen[j], coef);
                }
            }
            int[] ec = new int[ecLen];
            Array.Copy(msg, data.Length, ec, 0, ecLen);
            return ec;
        }

        private static int BitLen(int v)
        {
            int len = 0;
            while (v > 0) { len++; v >>= 1; }
            return len;
        }

        public static int[,] MakeQrMatrix(string payload)
        {
            byte[] raw = Encoding.UTF8.GetBytes(payload);
            int[,] specs = new int[,] {
                { 1, 19, 7, -1 },
                { 2, 34, 10, 18 },
                { 3, 55, 15, 22 },
                { 4, 80, 20, 26 },
                { 5, 108, 26, 30 }
            };
            int chosen = -1;
            for (int i = 0; i < 5; i++)
            {
                if (raw.Length <= specs[i, 1] - 2) { chosen = i; break; }
            }
            if (chosen < 0) return null;

            int ver = specs[chosen, 0];
            int dataCw = specs[chosen, 1];
            int ecCw = specs[chosen, 2];
            int alignPos = specs[chosen, 3];
            int size = 17 + 4 * ver;

            List<int> bits = new List<int>();
            bits.Add(0); bits.Add(1); bits.Add(0); bits.Add(0); // Byte mode (0100)
            for (int i = 7; i >= 0; i--) bits.Add((raw.Length >> i) & 1);
            foreach (byte b in raw)
            {
                for (int i = 7; i >= 0; i--) bits.Add((b >> i) & 1);
            }
            int term = Math.Min(4, dataCw * 8 - bits.Count);
            for (int i = 0; i < term; i++) bits.Add(0);
            while (bits.Count % 8 != 0) bits.Add(0);

            List<int> cw = new List<int>();
            for (int i = 0; i < bits.Count; i += 8)
            {
                int val = 0;
                for (int j = 0; j < 8; j++) val = (val << 1) | bits[i + j];
                cw.Add(val);
            }
            int[] pads = new int[] { 0xEC, 0x11 };
            int pIdx = 0;
            while (cw.Count < dataCw) { cw.Add(pads[pIdx % 2]); pIdx++; }

            int[] ec = RsEncode(cw.ToArray(), ecCw);
            List<int> stream = new List<int>();
            foreach (int b in cw) for (int i = 7; i >= 0; i--) stream.Add((b >> i) & 1);
            foreach (int b in ec) for (int i = 7; i >= 0; i--) stream.Add((b >> i) & 1);

            int[,] grid = new int[size, size];
            bool[,] res = new bool[size, size];

            Action<int, int, int> setFn = delegate(int r, int c, int v)
            {
                if (r >= 0 && r < size && c >= 0 && c < size) { grid[r, c] = v; res[r, c] = true; }
            };

            Action<int, int> addFinder = delegate(int r0, int c0)
            {
                for (int r = -1; r <= 7; r++)
                {
                    for (int c = -1; c <= 7; c++)
                    {
                        bool inOut = r >= 0 && r <= 6 && c >= 0 && c <= 6;
                        bool onBorder = r == 0 || r == 6 || c == 0 || c == 6;
                        bool inCore = r >= 2 && r <= 4 && c >= 2 && c <= 4;
                        setFn(r0 + r, c0 + c, (inOut && (onBorder || inCore)) ? 1 : 0);
                    }
                }
            };

            addFinder(0, 0);
            addFinder(0, size - 7);
            addFinder(size - 7, 0);

            if (alignPos >= 0)
            {
                for (int r = -2; r <= 2; r++)
                {
                    for (int c = -2; c <= 2; c++)
                    {
                        int v = (Math.Abs(r) == 2 || Math.Abs(c) == 2 || (r == 0 && c == 0)) ? 1 : 0;
                        setFn(alignPos + r, alignPos + c, v);
                    }
                }
            }
            for (int i = 8; i < size - 8; i++)
            {
                int v = (i % 2 == 0) ? 1 : 0;
                setFn(6, i, v);
                setFn(i, 6, v);
            }
            setFn(size - 8, 8, 1);
            for (int i = 0; i < 9; i++) { if (i != 6) { res[8, i] = true; res[i, 8] = true; } }
            for (int i = 1; i < 8; i++) res[size - i, 8] = true;
            for (int i = 1; i < 9; i++) res[8, size - i] = true;

            int bitIdx = 0;
            bool up = true;
            int col = size - 1;
            while (col > 0)
            {
                if (col == 6) col--;
                for (int step = 0; step < size; step++)
                {
                    int r = up ? (size - 1 - step) : step;
                    int[] cols = new int[] { col, col - 1 };
                    foreach (int c in cols)
                    {
                        if (!res[r, c])
                        {
                            grid[r, c] = (bitIdx < stream.Count) ? stream[bitIdx] : 0;
                            bitIdx++;
                        }
                    }
                }
                up = !up;
                col -= 2;
            }

            int[,] bestMat = null;
            int bestPen = int.MaxValue;
            for (int mid = 0; mid < 8; mid++)
            {
                int[,] cand = new int[size, size];
                for (int r = 0; r < size; r++)
                {
                    for (int c = 0; c < size; c++)
                    {
                        bool flip = false;
                        if (!res[r, c])
                        {
                            if (mid == 0) flip = (r + c) % 2 == 0;
                            else if (mid == 1) flip = r % 2 == 0;
                            else if (mid == 2) flip = c % 3 == 0;
                            else if (mid == 3) flip = (r + c) % 3 == 0;
                            else if (mid == 4) flip = (r / 2 + c / 3) % 2 == 0;
                            else if (mid == 5) flip = (r * c) % 2 + (r * c) % 3 == 0;
                            else if (mid == 6) flip = ((r * c) % 2 + (r * c) % 3) % 2 == 0;
                            else flip = ((r + c) % 2 + (r * c) % 3) % 2 == 0;
                        }
                        cand[r, c] = grid[r, c] ^ (flip ? 1 : 0);
                    }
                }

                int d = (1 << 3) | mid;
                int fv = d << 10;
                while (BitLen(fv) >= 11) fv ^= 0x537 << (BitLen(fv) - 11);
                int fmt = ((d << 10) | fv) ^ 0x5412;
                int[] tlCols = new int[] { 0, 1, 2, 3, 4, 5, 7, 8 };
                for (int ic = 0; ic < tlCols.Length; ic++) cand[8, tlCols[ic]] = (fmt >> (14 - ic)) & 1;
                int[] tlRows = new int[] { 7, 5, 4, 3, 2, 1, 0 };
                for (int ir = 0; ir < tlRows.Length; ir++) cand[tlRows[ir], 8] = (fmt >> (6 - ir)) & 1;
                for (int i = 0; i < 7; i++) cand[size - 1 - i, 8] = (fmt >> (14 - i)) & 1;
                for (int i = 0; i < 8; i++) cand[8, size - 8 + i] = (fmt >> (7 - i)) & 1;

                int dark = 0;
                for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) dark += cand[r, c];
                int pen = Math.Abs((dark * 100 / (size * size)) - 50) * 2;
                for (int r = 0; r < size; r++)
                {
                    int run = 1;
                    for (int c = 1; c < size; c++)
                    {
                        if (cand[r, c] == cand[r, c - 1]) { run++; if (run == 5) pen += 3; else if (run > 5) pen += 1; }
                        else run = 1;
                    }
                }
                for (int c = 0; c < size; c++)
                {
                    int run = 1;
                    for (int r = 1; r < size; r++)
                    {
                        if (cand[r, c] == cand[r - 1, c]) { run++; if (run == 5) pen += 3; else if (run > 5) pen += 1; }
                        else run = 1;
                    }
                }
                if (pen < bestPen) { bestPen = pen; bestMat = cand; }
            }
            return bestMat;
        }

        public static Bitmap GenerateQrBitmap(string payload, int targetPixelSize = 220, int quietZone = 2)
        {
            int[,] mat = MakeQrMatrix(payload);
            if (mat == null) return null;

            int dim = mat.GetLength(0);
            int totalDim = dim + quietZone * 2;
            int moduleSize = Math.Max(1, targetPixelSize / totalDim);
            int finalSize = totalDim * moduleSize;

            Bitmap bmp = new Bitmap(finalSize, finalSize, PixelFormat.Format32bppArgb);
            using (Graphics g = Graphics.FromImage(bmp))
            {
                g.Clear(Color.White);
                using (SolidBrush darkBrush = new SolidBrush(Color.FromArgb(15, 23, 42))) // Modern dark slate #0F172A
                {
                    for (int r = 0; r < dim; r++)
                    {
                        for (int c = 0; c < dim; c++)
                        {
                            if (mat[r, c] == 1)
                            {
                                g.FillRectangle(
                                    darkBrush,
                                    (c + quietZone) * moduleSize,
                                    (r + quietZone) * moduleSize,
                                    moduleSize,
                                    moduleSize
                                );
                            }
                        }
                    }
                }
            }
            return bmp;
        }
    }
}
