using System;
using System.Diagnostics;
using System.Threading;
using System.Windows.Forms;

namespace AirTouchPC
{
    internal static class Program
    {
        private const string AppMutexName = "AirTouchPC_SingleInstance_Mutex_Session";

        [STAThread]
        private static void Main()
        {
            bool createdNew;
            Mutex mutex = null;
            try
            {
                mutex = new Mutex(true, AppMutexName, out createdNew);
            }
            catch
            {
                createdNew = true;
            }

            if (!createdNew)
            {
                // Bring existing instance to foreground if running
                try
                {
                    Process current = Process.GetCurrentProcess();
                    foreach (Process p in Process.GetProcessesByName(current.ProcessName))
                    {
                        if (p.Id != current.Id && p.MainWindowHandle != IntPtr.Zero)
                        {
                            NativeMethods.ShowWindow(p.MainWindowHandle, NativeMethods.SW_RESTORE);
                            NativeMethods.SetForegroundWindow(p.MainWindowHandle);
                            break;
                        }
                    }
                }
                catch { }
                return;
            }

            try
            {
                NativeMethods.SetProcessDPIAware();
            }
            catch { }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            try
            {
                Application.Run(new MainForm());
            }
            catch (Exception ex)
            {
                System.IO.File.WriteAllText("crash.log", ex.ToString());
            }

            if (mutex != null)
            {
                try
                {
                    mutex.ReleaseMutex();
                }
                catch { }
                mutex.Close();
            }
        }
    }
}
