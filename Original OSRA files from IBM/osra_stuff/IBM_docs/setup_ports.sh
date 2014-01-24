cygstart -- /setup/setup-x86.exe -M -K http://cygwinports.org/ports.gpg -s ftp://sourceware.org/pub/cygwinports # -M : goes straight to mirror
#cygstart -- /c/CygWin/setup/setup-x86.exe -K http://cygwinports.org/ports.gpg -s http://downloads.sourceforge.net/cygwin-ports
#cygstart -- /c/CygWin/setup/setup-x86.exe -K http://cygwinports.org/ports.gpg -s http://mirrors.rcn.net/pub/sourceware/
#cygstart -- /c/CygWin/setup/setup-x86.exe -K http://cygwinports.org/ports.gpg -s http://sources-redhat.mirror.redwire.net/

if [ "$?" != "0" ]; then
  wait # so the console window won't close if run from Windows Explorer context menu
fi
