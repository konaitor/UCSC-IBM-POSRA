/*  Ocrcheck - A test program for the ocradlib library
    Copyright (C) 2009, 2010, 2011 Antonio Diaz Diaz.

    This program is free software: you have unlimited permission
    to copy, distribute and modify it.

    Usage is:
      ocrcheck filename.pnm

    This program reads the specified image file, feeds it to the OCR
    engine and sends the resulting text to stdout.
*/

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <stdint.h>
#include <unistd.h>

#include "ocradlib.h"


int main( const int argc, const char * const argv[] )
  {
  if( argc < 2 )
    {
    std::fprintf( stderr, "Usage: ocrcheck filename.pnm\n" );
    return 1;
    }

  if( OCRAD_version()[0] != OCRAD_version_string[0] )
    {
    std::fprintf( stderr, "bad library version" );
    return 3;
    }

  if( std::strcmp( PROGVERSION, OCRAD_version_string ) )
    {
    std::fprintf( stderr, "bad library version_string" );
    return 3;
    }

  OCRAD_Descriptor * const ocrdes = OCRAD_open();
  if( !ocrdes || OCRAD_get_errno( ocrdes ) != OCRAD_ok )
    {
    OCRAD_close( ocrdes );
    std::fprintf( stderr, "not enough memory.\n" );
    return 1;
    }

  if( OCRAD_set_image_from_file( ocrdes, argv[1], false ) < 0 )
    {
    const OCRAD_Errno ocr_errno = OCRAD_get_errno( ocrdes );
    OCRAD_close( ocrdes );
    if( ocr_errno == OCRAD_mem_error )
      std::fprintf( stderr, "not enough memory.\n" );
    else
      std::fprintf( stderr, "Can't open file `%s' for reading\n", argv[1] );
    return 1;
    }
//  std::fprintf( stderr, "ocrcheck: testing file `%s'\n", argv[1] );

  if( OCRAD_set_threshold( ocrdes, -1 ) < 0 ||	// auto threshold
      OCRAD_recognize( ocrdes, false ) < 0 )	// no layout
    {
    const OCRAD_Errno ocr_errno = OCRAD_get_errno( ocrdes );
    OCRAD_close( ocrdes );
    if( ocr_errno == OCRAD_mem_error )
      {
      std::fprintf( stderr, "not enough memory.\n" );
      return 1;
      }
    std::fprintf( stderr, "internal error: invalid argument.\n" );
    return 3;
    }

  const int blocks = OCRAD_result_blocks( ocrdes );
  int chars_total_by_block = 0;
  int chars_total_by_line = 0;
  int chars_total_by_count = 0;
  for( int b = 0; b < blocks; ++b )
    {
    const int lines = OCRAD_result_lines( ocrdes, b );
    chars_total_by_block += OCRAD_result_chars_block( ocrdes, b );
    for( int l = 0; l < lines; ++l )
      {
      const char * const s = OCRAD_result_line( ocrdes, b, l );
      chars_total_by_line += OCRAD_result_chars_line( ocrdes, b, l );
      if( s && s[0] )
        {
        std::printf( "%s", s );
        chars_total_by_count += std::strlen( s ) - 1;
        }
      }
    std::printf( "\n" );
    }
  const int chars_total = OCRAD_result_chars_total( ocrdes );
  if( chars_total_by_block != chars_total ||
      chars_total_by_line != chars_total ||
      chars_total_by_count != chars_total )
    {
    std::fprintf( stderr, "library_error: character counts differ.\n" );
    return 1;
    }

  OCRAD_close( ocrdes );
  return 0;
  }
