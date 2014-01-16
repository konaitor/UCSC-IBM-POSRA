/*  GNU Ocrad - Optical Character Recognition program
    Copyright (C) 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011
    Antonio Diaz Diaz.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
/*
    Return values: 0 for a normal exit, 1 for environmental problems
    (file not found, invalid flags, I/O errors, etc), 2 to indicate a
    corrupt or invalid input file, 3 for an internal consistency error
    (eg, bug) which caused ocrad to panic.
*/

#include <cctype>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#if defined(__MSVCRT__) || defined(__OS2__) || defined(_MSC_VER)
#include <fcntl.h>
#include <unistd.h>
#include <io.h>
#endif

#include "arg_parser.h"
#include "common.h"
#include "rational.h"
#include "rectangle.h"
#include "page_image.h"
#include "textpage.h"


namespace {

const char * const Program_name = "GNU Ocrad";
const char * const program_name = "ocrad";
const char * const program_year = "2011";
const char * invocation_name = 0;

struct Input_control
  {
  Transformation transformation;
  int scale;
  Rational threshold, ltwh[4];
  bool copy, cut, invert, layout;

  Input_control() throw()
    : scale( 0 ), threshold( -1 ),
      copy( false ), cut( false ), invert( false ), layout( false ) {}

  bool parse_cut_rectangle( const char * const s ) throw();
  bool parse_threshold( const char * const s ) throw();
  };


void show_error( const char * const msg, const int errcode = 0,
                 const bool help = false ) throw()
  {
  if( verbosity >= 0 )
    {
    if( msg && msg[0] )
      {
      std::fprintf( stderr, "%s: %s", program_name, msg );
      if( errcode > 0 ) std::fprintf( stderr, ": %s", std::strerror( errcode ) );
      std::fprintf( stderr, "\n" );
      }
    if( help && invocation_name && invocation_name[0] )
      std::fprintf( stderr, "Try `%s --help' for more information.\n",
                    invocation_name );
    }
  }


bool Input_control::parse_cut_rectangle( const char * const s ) throw()
  {
  int c = ltwh[0].parse( s );				// left
  if( c && s[c] == ',' )
    {
    int i = c + 1;
    c = ltwh[1].parse( &s[i] );				// top
    if( c && s[i+c] == ',' )
      {
      i += c + 1; c = ltwh[2].parse( &s[i] );		// width
      if( c && s[i+c] == ',' && ltwh[2] > 0 )
        {
        i += c + 1; c = ltwh[3].parse( &s[i] );		// height
        if( c && ltwh[3] > 0 ) { cut = true; return true; }
        }
      }
    }
  show_error( "invalid cut rectangle.", 0, true );
  return false;
  }


bool Input_control::parse_threshold( const char * const s ) throw()
  {
  Rational tmp;
  if( tmp.parse( s ) && tmp >= 0 && tmp <= 1 )
    { threshold = tmp; return true; }
  show_error( "threshold out of limits (0.0 - 1.0).", 0, true );
  return false;
  }


void show_help() throw()
  {
  std::printf( "%s - Optical Character Recognition program.\n", Program_name );
  std::printf( "Reads pnm file(s), or standard input, and sends text to standard output.\n" );
  std::printf( "\nUsage: %s [options] [files]\n", invocation_name );
  std::printf( "\nOptions:\n" );
  std::printf( "  -h, --help               display this help and exit\n" );
  std::printf( "  -V, --version            output version information and exit\n" );
  std::printf( "  -a, --append             append text to output file\n" );
  std::printf( "  -c, --charset=<name>     try `--charset=help' for a list of names\n" );
  std::printf( "  -e, --filter=<name>      try `--filter=help' for a list of names\n" );
  std::printf( "  -f, --force              force overwrite of output file\n" );
  std::printf( "  -F, --format=<fmt>       output format (byte, utf8)\n" );
  std::printf( "  -i, --invert             invert image levels (white on black)\n" );
  std::printf( "  -l, --layout             perform layout analysis\n" );
  std::printf( "  -o, --output=<file>      place the output into <file>\n" );
  std::printf( "  -q, --quiet              suppress all messages\n" );
  std::printf( "  -s, --scale=[-]<n>       scale input image by [1/]<n>\n" );
  std::printf( "  -t, --transform=<name>   try `--transform=help' for a list of names\n" );
  std::printf( "  -T, --threshold=<n%%>     threshold for binarization (0-100%%)\n" );
  std::printf( "  -u, --cut=<l,t,w,h>      cut input image by given rectangle\n" );
  std::printf( "  -v, --verbose            be verbose\n" );
  std::printf( "  -x, --export=<file>      export OCR Results File to <file>\n" );
  if( verbosity > 0 )
    {
    std::printf( "  -1..6                    pnm output file type (debug)\n" );
    std::printf( "  -C, --copy               'copy' input to output (debug)\n" );
    std::printf( "  -D, --debug=<level>      (0-100) output intermediate data (debug)\n" );
    }
  std::printf( "\nReport bugs to bug-ocrad@gnu.org\n" );
  std::printf( "Ocrad home page: http://www.gnu.org/software/ocrad/ocrad.html\n" );
  std::printf( "General help using GNU software: http://www.gnu.org/gethelp\n" );
  }


void show_version() throw()
  {
  std::printf( "%s %s\n", Program_name, PROGVERSION );
  std::printf( "Copyright (C) %s Antonio Diaz Diaz.\n", program_year );
  std::printf( "License GPLv3+: GNU GPL version 3 or later <http://gnu.org/licenses/gpl.html>\n" );
  std::printf( "This is free software: you are free to change and redistribute it.\n" );
  std::printf( "There is NO WARRANTY, to the extent permitted by law.\n" );
  }


const char * my_basename( const char * filename ) throw()
  {
  const char * c = filename;
  while( *c ) { if( *c == '/' ) { filename = c + 1; } ++c; }
  return filename;
  }


int process_file( FILE * const infile, const char * const infile_name,
                  const Input_control & input_control,
                  const Control & control )
  {
  if( verbosity > 0 )
    std::fprintf( stderr, "processing file `%s'\n", infile_name );
  try
    {
    Page_image page_image( infile, input_control.invert );

    if( input_control.cut && !page_image.cut( input_control.ltwh ) )
      {
      if( verbosity > 0 )
        std::fprintf( stderr, "file `%s' totally cut away\n", infile_name );
      return 1;
      }
    page_image.threshold( input_control.threshold );
    page_image.transform( input_control.transformation );
    page_image.scale( input_control.scale );

    if( input_control.copy )
      {
      if( control.outfile ) page_image.save( control.outfile, control.filetype );
      return 0;
      }

    Textpage textpage( page_image, my_basename( infile_name ), control,
                       input_control.layout );
    if( control.debug_level == 0 )
      {
      if( control.outfile ) textpage.print( control );
      if( control.exportfile ) textpage.xprint( control );
      }
    }
  catch( Page_image::Error e ) { show_error( e.msg ); return 2; }
  if( verbosity > 0 ) std::fprintf( stderr, "\n" );
  return 0;
  }

} // end namespace


// 'infile' contains the scanned image (in pnm format) to be converted
// to text.
// 'outfile' is the destination for the text version of the scanned
// image. (or for a pnm file if debugging).
// 'exportfile' is the Ocr Results File.
//
int main( const int argc, const char * const argv[] )
  {
  Input_control input_control;
  Control control;
  const char *outfile_name = 0, *exportfile_name = 0;
  bool append = false, force = false;
  invocation_name = argv[0];

  const Arg_parser::Option options[] =
    {
    { '1', 0,           Arg_parser::no  },
    { '2', 0,           Arg_parser::no  },
    { '3', 0,           Arg_parser::no  },
    { '4', 0,           Arg_parser::no  },
    { '5', 0,           Arg_parser::no  },
    { '6', 0,           Arg_parser::no  },
    { 'a', "append",    Arg_parser::no  },
    { 'c', "charset",   Arg_parser::yes },
    { 'C', "copy",      Arg_parser::no  },
    { 'D', "debug",     Arg_parser::yes },
    { 'e', "filter",    Arg_parser::yes },
    { 'f', "force",     Arg_parser::no  },
    { 'F', "format",    Arg_parser::yes },
    { 'h', "help",      Arg_parser::no  },
    { 'i', "invert",    Arg_parser::no  },
    { 'l', "layout",    Arg_parser::no  },
    { 'o', "output",    Arg_parser::yes },
    { 'q', "quiet",     Arg_parser::no  },
    { 's', "scale",     Arg_parser::yes },
    { 't', "transform", Arg_parser::yes },
    { 'T', "threshold", Arg_parser::yes },
    { 'u', "cut",       Arg_parser::yes },
    { 'v', "verbose",   Arg_parser::no  },
    { 'V', "version",   Arg_parser::no  },
    { 'x', "export",    Arg_parser::yes },
    {  0 , 0,           Arg_parser::no  } };

  const Arg_parser parser( argc, argv, options );
  if( parser.error().size() )				// bad option
    { show_error( parser.error().c_str(), 0, true ); return 1; }

  int argind = 0;
  for( ; argind < parser.arguments(); ++argind )
    {
    const int code = parser.code( argind );
    if( !code ) break;					// no more options
    const char * const arg = parser.argument( argind ).c_str();
    switch( code )
      {
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6': control.filetype = code; break;
      case 'a': append = true; break;
      case 'c': if( !control.charset.enable( arg ) )
                  { control.charset.show_error( program_name, arg ); return 1; }
                break;
      case 'C': input_control.copy = true; break;
      case 'D': control.debug_level = std::strtol( arg, 0, 0 ); break;
      case 'e': if( !control.filter.set( arg ) )
                  { control.filter.show_error( program_name, arg ); return 1; }
                break;
      case 'f': force = true; break;
      case 'F': if( !control.set_format( arg ) )
                  { show_error( "bad output format.", 0, true ); return 1; }
                break;
      case 'h': show_help(); return 0;
      case 'i': input_control.invert = true; break;
      case 'l': input_control.layout = true; break;
      case 'o': outfile_name = arg; break;
      case 'q': verbosity = -1; break;
      case 's': input_control.scale = std::strtol( arg, 0, 0 ); break;
      case 't': if( !input_control.transformation.set( arg ) )
                  { input_control.transformation.show_error( program_name, arg );
                  return 1; }
                break;
      case 'T': if( !input_control.parse_threshold( arg ) ) return 1; break;
      case 'u': if( !input_control.parse_cut_rectangle( arg ) ) return 1; break;
      case 'v': if( verbosity < 4 ) ++verbosity; break;
      case 'V': show_version(); return 0;
      case 'x': exportfile_name = arg; break;
      default : Ocrad::internal_error( "uncaught option" );
      }
    } // end process options

#if defined(__MSVCRT__) || defined(__OS2__) || defined(_MSC_VER)
  _setmode( fileno( stdin ), O_BINARY );
  _setmode( fileno( stdout ), O_BINARY );
#endif

  if( outfile_name && std::strcmp( outfile_name, "-" ) != 0 )
    {
    if( append ) control.outfile = std::fopen( outfile_name, "a" );
    else if( force ) control.outfile = std::fopen( outfile_name, "w" );
    else if( ( control.outfile = std::fopen( outfile_name, "wx" ) ) == 0 )
      {
      if( verbosity >= 0 )
        std::fprintf( stderr, "Output file %s already exists.\n", outfile_name );
      return 1;
      }
    if( !control.outfile )
      {
      if( verbosity >= 0 )
        std::fprintf( stderr, "Cannot open %s\n", outfile_name );
      return 1;
      }
    }

  if( exportfile_name && control.debug_level == 0 && !input_control.copy )
    {
    if( std::strcmp( exportfile_name, "-" ) == 0 )
      { control.exportfile = stdout; if( !outfile_name ) control.outfile = 0; }
    else
      {
      control.exportfile = std::fopen( exportfile_name, "w" );
      if( !control.exportfile )
        {
        if( verbosity >= 0 )
          std::fprintf( stderr, "Cannot open %s\n", exportfile_name );
        return 1;
        }
      }
    std::fprintf( control.exportfile,
                  "# Ocr Results File. Created by %s version %s\n",
                  Program_name, PROGVERSION );
    }

  // process any remaining command line arguments (input files)
  FILE * infile = (argind < parser.arguments()) ? 0 : stdin;
  const char *infile_name = "-";
  int retval = 0;
  while( true )
    {
    while( infile != stdin )
      {
      if( infile ) std::fclose( infile );
      if( argind >= parser.arguments() ) { infile = 0; break; }
      infile_name = parser.argument( argind++ ).c_str();
      if( std::strcmp( infile_name, "-" ) == 0 ) infile = stdin;
      else infile = std::fopen( infile_name, "rb" );
      if( infile ) break;
      if( verbosity >= 0 )
        std::fprintf( stderr, "Cannot open %s\n", infile_name );
      if( retval == 0 ) retval = 1;
      }
    if( !infile ) break;

    int tmp = process_file( infile, infile_name, input_control, control );
    if( infile == stdin )
      {
      if( tmp <= 1 )
        {
        int ch;
        do ch = std::fgetc( infile ); while( std::isspace( ch ) );
        std::ungetc( ch, infile );
        }
      if( tmp > 1 || std::feof( infile ) || std::ferror( infile ) ) infile = 0;
      }
    if( tmp > retval ) retval = tmp;
    if( control.outfile ) std::fflush( control.outfile );
    if( control.exportfile ) std::fflush( control.exportfile );
    }
  if( control.outfile ) std::fclose( control.outfile );
  if( control.exportfile ) std::fclose( control.exportfile );
  return retval;
  }
