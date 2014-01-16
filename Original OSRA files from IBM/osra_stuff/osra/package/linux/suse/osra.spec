#
# spec file for package OSRA
#
# Copyright (c) 2009 SUSE LINUX Products GmbH, Nuernberg, Germany.
#
# All modifications and additions to the file contributed by third parties
# remain the property of their copyright owners, unless otherwise agreed
# upon. The license for this file, and modifications and additions to the
# file, is the same license as for the pristine package itself (unless the
# license for the pristine package is not an Open Source License, in which
# case the license is the MIT License). An "Open Source License" is a
# license that conforms to the Open Source Definition (Version 1.9)
# published by the Open Source Initiative.

# Please submit bugfixes or comments via http://bugs.opensuse.org/
#

# norootforbuild

%define name		osra
%define version		2.0.0

%define builddep	glibc-devel, libstdc++45-devel, tclap >= 1.2, potrace-devel >= 1.8, gocr-devel >= 0.49, ocrad-devel >= 0.20, libopenbabel-devel >= 2.2, libGraphicsMagick++-devel >= 1.3.10, cuneiform-devel => 1.1.0, tesseract-devel => 3.00, docbook-xsl-stylesheets => 1.74.0, libxslt
%define binarydep	potrace-lib >= 1.8, libopenbabel3 >= 2.2, libGraphicsMagick++3 >= 1.3.10, cuneiform => 1.1.0, tesseract => 3.00, %{name}-common = %{version}

Name:			%{name}
BuildRequires:	%{builddep}
Url:			http://osra.sourceforge.net/
Summary:		A command line chemical structure recognition tool
Version:		%{version}
Release:		1.0
Group:			Productivity/Graphics/Other
Requires:		%{binarydep}
License:		GPL v2 or later
Source0:		%{name}-%{version}.tar.gz
#Patch0:		Makefile.in.patch
BuildRoot:		%{_tmppath}/%{name}-%{version}-build

%description
OSRA is a utility designed to convert graphical representations of chemical structures into SMILES or SDF.
OSRA can read a document in any of the over 90 graphical formats parseable by GraphicMagick and generate
the SMILES or SDF representation of the molecular structure images encountered within that document.

Authors:
--------
    Igor Filippov <igor.v.filippov@gmail.com>

%package common
Summary:		OSRA shared files
Group:			Productivity/Graphics/Other
Requires:		%{binarydep}
BuildArch:		noarch

%description common
This package contains the shared files for OSRA executable / library.

%package lib1
Summary:		OSRA C++ library
Group:			Development/Libraries/C and C++
Requires:		%{binarydep}

%description lib1
This package contains the dynamic library needed to consume OSRA functionality
from C++ programs.

%package lib-java1
Summary:		OSRA Java library
Group:			Development/Libraries/C and C++
Requires:		%{binarydep}

%description lib-java1
This package contains the dynamic library needed to consume OSRA functionality
from Java programs.

%package devel
Summary:        OSRA static library and header files mandatory for development
Group:          Development/Libraries/C and C++
Requires:       %{name}-lib1 = %{version}

%description devel
This package contains all necessary include files and libraries needed
to develop applications on the top of OSRA.

%prep
%setup -n %{name}-%{version}
#%patch0 -p0

%build
# See http://stackoverflow.com/questions/3113472/how-to-make-an-rpm-spec-that-installs-libraries-to-usr-lib-xor-usr-lib64-based
# See http://www.rpm.org/api/4.4.2.2/config_macros.html
%configure --enable-docs --enable-lib --enable-java --with-tesseract --with-cuneiform --datadir=%{_datadir}/%{name} --docdir=%{_datadir}/doc/packages/%{name}
%__make

%install
# See http://fedoraproject.org/wiki/PackagingGuidelines#Why_the_.25makeinstall_macro_should_not_be_used
%__make install DESTDIR=%{buildroot}

%clean
%__rm -rf $RPM_BUILD_ROOT

%define _sharedir %{_prefix}/share

%files
%defattr(-, root, root)
%{_prefix}/bin/%{name}
%{_mandir}/man?/%{name}.*

%files common
%{_sharedir}/%{name}
%{_sharedir}/doc

%files lib1
%defattr(-,root,root)
%{_libdir}/lib%{name}.so*

%files lib-java1
%defattr(-,root,root)
%{_libdir}/lib%{name}_java.so*

%files devel
%defattr(-,root,root)
%{_libdir}/lib%{name}.a
%{_libdir}/pkgconfig
%{_includedir}

# spec file ends here

%changelog
* Thu Jul 01 2011 dma_k@mail.ru
- Initial SuSE package
