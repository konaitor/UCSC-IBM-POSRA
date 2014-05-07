#
# This makefile is included from all other makefiles.
#
# Notes:
# - Use $(CXX) variable for compiler.
# - Use $(LINK.cpp) for linker. Currently it is equal to "$(CXX) $(CXXFLAGS) $(CPPFLAGS) $(LDFLAGS) $(TARGET_ARCH)", but if you need real linker detection, use libtool + LT_PATH_LD macros
# - Pass optimization flags as follows:
#   CXXFLAGS="-g -O3" ./configure
#   There are no defaults for any options as there is no guarantee, that target compiler supports them, see http://www.gnu.org/software/autoconf/manual/make/Command-Variables.html.
#

NAME			:= osra
VERSION			:= 2.0.0
LIB_MAJOR_VERSION	:= 1
LIB_MINOR_VERSION	:= 0
LIB_PATCH_VERSION	:= 20000
NAME_VERSION		:= $(NAME)-$(VERSION)

prefix			:= /usr/local
exec_prefix		:= ${prefix}
bindir			:= ${exec_prefix}/bin
libdir			:= ${exec_prefix}/lib
includedir		:= ${prefix}/include
datarootdir		:= ${prefix}/share
datadir			:= ${prefix}/share
docdir			:= ${prefix}/share/doc/osra
mandir			:= ${datarootdir}/man

TARGET_CPU		:= x86_64
TARGET_OS		:= linux

CXX			:= g++
RM			:= /usr/bin/rm
LN_S			:= ln -s
RANLIB			:= ranlib
AR			:= /usr/bin/ar
INSTALL			:= /usr/bin/install -c
INSTALL_PROGRAM		:= ${INSTALL} -s
INSTALL_DATA		:= ${INSTALL} -m 644
INSTALL_DIR		:= ${INSTALL} -d -m 755
NATURALDOCS		:= 
XSLTPROC		:= 
TESSERACT_LIB		:= 
OSRA_LIB		:= 
OSRA_JAVA		:= 

# Notes: see "configure --enable-static-linking" to enable static linking; use "configure --enable-profiling" to include extra debug info.

CXXFLAGS		:= -fopenmp -g -O2
CPPFLAGS		:=  -I/usr/local/include/GraphicsMagick -I/usr/include/openbabel-2.0 -I/usr/local/include/gocr -I/usr/local/include 
LDFLAGS			:= -L/usr/local/lib 
# Important that this variable is re-evaluated each time when used: 
LDSHAREDFLAGS		= -shared -Wl,-soname,$@.$(LIB_MAJOR_VERSION)

EXEEXT			:= 
SHAREDEXT		:= .so

LIBS			:= -lPgm2asc -lGraphicsMagick -lGraphicsMagick++ -lopenbabel -locrad -lpotrace -lm 

PHONY_TARGETS		:= all install uninstall clean distclean

.PHONY: $(PHONY_TARGETS)

.SUFFIXES: .c .cpp
