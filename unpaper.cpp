/* ---------------------------------------------------------------------------
unpaper - written by Jens Gulden 2005-2007                                  */

const char* VERSION = "0.3";

/* ---------------------------------------------------------------------------
  unpaper is a post-processing tool for scanned sheets of paper, especially for
  book pages that have been scanned from previously created photocopies.
  The main purpose is to make scanned book pages better readable on screen
  after conversion to PDF. Additionally, unpaper might be useful to enhance
  the quality of scanned pages before performing optical character recognition
  (OCR).

  unpaper tries to clean scanned images by removing dark edges that appeared
  through scanning or copying on areas outside the actual page content (e.g.
  dark areas between the left-hand-side and the right-hand-side of a double-
  sided book-page scan).
  The program also tries to detect disaligned centering and rotation of pages
  and will automatically straighten each page by rotating it to the correct
  angle. This process is called "deskewing".
  Note that the automatic processing will sometimes fail. It is always a good
  idea to manually control the results of unpaper and adjust the parameter
  settings according to the requirements of the input. Each processing step can
  also be disabled individually for each sheet.

  Input and output files can be in either .pbm , .pgm or .ppm format, thus
  generally in .pnm format, as also used by the Linux scanning tools scanimage
  and scanadf.
  Conversion to PDF can e.g. be achieved with the Linux tools pgm2tiff, tiffcp
  and tiff2pdf.
--------------------------------------------------------------------------- */

//const char* COMPILE =
//  "gcc -D TIMESTAMP=\"<yyyy-MM-dd HH:mm:ss>\" -lm -O3 -funroll-all-loops -fomit-frame-pointer -ftree-vectorize -o unpaper unpaper.c\n";

/* ------------------------------------------------------------------------ */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include <time.h>

#include <Magick++.h>

/* --- preprocessor macros ------------------------------------------------ */

#define abs(value) ( (value) >=0 ? (value) : -(value) )
#define max(a, b) ( (a >= b) ? (a) : (b) )
#define pluralS(i) ( (i > 1) ? "s" : "" )
#define pixelValue(r, g, b) ( (r)<<16 | (g)<<8 | (b) )
#define pixelGrayscaleValue(g) ( (g)<<16 | (g)<<8 | (g) )
#define pixelGrayscale(r, g, b) ( ( ( r == g ) && ( r == b ) ) ? r : ( ( r + g + b ) / 3 ) ) // average (optimized for already gray values)
#define pixelLightness(r, g, b) ( r < g ? ( r < b ? r : b ) : ( g < b ? g : b ) ) // minimum
#define pixelDarknessInverse(r, g, b) ( r > g ? ( r > b ? r : b ) : ( g > b ? g : b ) ) // maximum
#define red(pixel) ( (pixel >> 16) & 0xff )
#define green(pixel) ( (pixel >> 8) & 0xff )
#define blue(pixel) ( pixel & 0xff )


/* --- preprocessor constants ---------------------------------------------- */

#define MAX_MULTI_INDEX 10000 // maximum pixel count of virtual line to detect rotation with
#define MAX_ROTATION_SCAN_SIZE 10000 // maximum pixel count of virtual line to detect rotation with
#define MAX_MASKS 100
#define MAX_POINTS 100
#define MAX_FILES 100
#define MAX_PAGES 2
#define WHITE 255
#define GRAY 127
#define BLACK 0
#define BLANK_TEXT "<blank>"


/* --- typedefs ----------------------------------------------------------- */

typedef enum
{
  FALSE,
  TRUE
} BOOLEAN;

typedef enum
{
  VERBOSE_QUIET = -1,
  VERBOSE_NONE = 0,
  VERBOSE_NORMAL = 1,
  VERBOSE_MORE = 2,
  VERBOSE_DEBUG = 3,
  VERBOSE_DEBUG_SAVE = 4
} VERBOSE_LEVEL;

typedef enum
{
  X,
  Y,
  COORDINATES_COUNT
} COORDINATES;

typedef enum
{
  WIDTH,
  HEIGHT,
  DIMENSIONS_COUNT
} DIMENSIONS;

typedef enum
{
  HORIZONTAL,
  VERTICAL,
  DIRECTIONS_COUNT
} DIRECTIONS;

typedef enum
{
  LEFT,
  TOP,
  RIGHT,
  BOTTOM,
  EDGES_COUNT
} EDGES;

typedef enum
{
  LAYOUT_NONE,
  LAYOUT_SINGLE,
  LAYOUT_DOUBLE,
  LAYOUTS_COUNT
} LAYOUTS;

typedef enum
{
  BRIGHT,
  DARK,
  SHADINGS_COUNT
} SHADINGS;

typedef enum
{
  RED,
  GREEN,
  BLUE,
  COLORCOMPONENTS_COUNT
} COLORCOMPONENTS;

typedef enum
{
  PBM,
  PGM,
  PPM,
  FILETYPES_COUNT
} FILETYPES;


/* --- struct ------------------------------------------------------------- */

struct IMAGE
{
  unsigned char* buffer;
  unsigned char* bufferGrayscale;
  unsigned char* bufferLightness;
  unsigned char* bufferDarknessInverse;
  int width;
  int height;
  int bitdepth;
  BOOLEAN color;
  int background;
};


/* --- constants ---------------------------------------------------------- */

// file type names (see typedef FILETYPES)
const char FILETYPE_NAMES[FILETYPES_COUNT][5] =
{
  "pbm",
  "pgm",
  "ppm"
};

// factors for conversion to inches
#define MEASUREMENTS_COUNT 3
const char MEASUREMENTS[MEASUREMENTS_COUNT][2][15] =
{
  { "in", "1.0" },
  { "cm", "0.393700787" },
  { "mm", "0.0393700787" }
};

// papersize alias names
#define PAPERSIZES_COUNT 10
const char PAPERSIZES[PAPERSIZES_COUNT][2][50] =
{
  { "a5", "14.8cm,21cm" },
  { "a5-landscape", "21cm,14.8cm" },
  { "a4", "21cm,29.7cm" },
  { "a4-landscape", "29.7cm,21cm" },
  { "a3", "29.7cm,42cm" },
  { "a3-landscape", "42cm,29.7cm" },
  { "letter", "8.5in,11in" },
  { "letter-landscape", "11in,8.5in" },
  { "legal", "8.5in,14in" },
  { "legal-landscape", "14in,8.5in" }
};


// color alias names
#define COLORS_COUNT 2
const char COLORS[COLORS_COUNT][2][50] =
{
  { "black", "#000000" },
  { "white", "#ffffff" }
};


/* --- global variable ---------------------------------------------------- */

VERBOSE_LEVEL verbose;

#include "unpaper.h"

/****************************************************************************
 * tool functions                                                           *
 ****************************************************************************/


/* --- arithmetic tool functions ------------------------------------------ */

/**
 * Returns the quadratic square of a number.
 */
double sqr(double d)
{
  return d*d;
}


/**
 * Converts degrees to radians.
 */
double degreesToRadians(double d)
{
  return d * M_PI / 180.0;
}


/**
 * Converts radians to degrees.
 */
double radiansToDegrees(double r)
{
  return r * 180.0 / M_PI;
}


/**
 * Limits an integer value to a maximum.
 */
void limit(int* i, int max)
{
  if (*i > max)
    {
      *i = max;
    }
}


/* --- tool functions for parameter parsing and verbose output ------------ */

/**
 * Parses a parameter string on occurrences of 'vertical', 'horizontal' or both.
 */
int parseDirections(char* s, int* exitCode)
{
  int dir = 0;
  if (strchr(s, 'h') != 0)   // (there is no 'h' in 'vertical'...)
    {
      dir = 1<<HORIZONTAL;
    }
  if (strchr(s, 'v') != 0)   // (there is no 'v' in 'horizontal'...)
    {
      dir |= 1<<VERTICAL;
    }
  if (dir == 0)
    {
      *exitCode = 1;
    }
  return dir;
}


/**
 * Prints whether directions are vertical, horizontal, or both.
 */
void printDirections(int d)
{
  BOOLEAN comma = FALSE;

  printf("[");
  if ((d & 1<<VERTICAL) != 0)
    {
      printf("vertical");
      comma = TRUE;
    }
  if ((d & 1<<HORIZONTAL) != 0)
    {
      if (comma == TRUE)
        {
          printf(",");
        }
      printf("horizontal");
    }
  printf("]\n");
}


/**
 * Parses a parameter string on occurrences of 'left', 'top', 'right', 'bottom' or combinations.
 */
int parseEdges(char* s, int* exitCode)
{
  int dir = 0;
  if (strstr(s, "left") != 0)
    {
      dir = 1<<LEFT;
    }
  if (strstr(s, "top") != 0)
    {
      dir |= 1<<TOP;
    }
  if (strstr(s, "right") != 0)
    {
      dir |= 1<<RIGHT;
    }
  if (strstr(s, "bottom") != 0)
    {
      dir |= 1<<BOTTOM;
    }
  if (dir == 0)
    {
      *exitCode = 1;
    }
  return dir;
}


/**
 * Prints whether edges are left, top, right, bottom or combinations.
 */
void printEdges(int d)
{
  BOOLEAN comma = FALSE;

  printf("[");
  if ((d & 1<<LEFT) != 0)
    {
      printf("left");
      comma = TRUE;
    }
  if ((d & 1<<TOP) != 0)
    {
      if (comma == TRUE)
        {
          printf(",");
        }
      printf("top");
      comma = TRUE;
    }
  if ((d & 1<<RIGHT) != 0)
    {
      if (comma == TRUE)
        {
          printf(",");
        }
      printf("right");
      comma = TRUE;
    }
  if ((d & 1<<BOTTOM) != 0)
    {
      if (comma == TRUE)
        {
          printf(",");
        }
      printf("bottom");
    }
  printf("]\n");
}


/**
 * Parses either a single integer string, of a pair of two integers seperated
 * by a comma.
 */
void parseInts(char* s, int i[2])
{
  i[0] = -1;
  i[1] = -1;
  sscanf(s, "%d,%d", &i[0], &i[1]);
  if (i[1]==-1)
    {
      i[1] = i[0]; // if second value is unset, copy first one into
    }
}


/**
 * Parses a pair of size-values and returns it in pixels.
 * Values may be suffixed by MEASUREMENTS such as 'cm', 'in', in that case
 * conversion to pixels is perfomed based on the dpi-value.
 */
void parseSize(char* s, int i[2], int dpi, int* exitCode)
{
  char str[2][255];
  char pattern[2][255];
  float factor[2];
  float f[2];
  int j, k;
  char* comma;
  int pos;

  // is s a papersize name?
  for (j = 0; j < PAPERSIZES_COUNT; j++)
    {
      if (strcmp(s, PAPERSIZES[j][0])==0)
        {
          s = (char*)PAPERSIZES[j][1]; // replace with size string
        }
    }

  // find comma in size string, if there
  comma = strchr(s, ',');
  if (comma != NULL)
    {
      pos = comma - s;
      strncpy(str[0], s, pos);
      str[0][pos] = 0; // (according to spec of strncpy, no terminating 0 is written)
      strcpy(str[1], &s[pos+1]); // copy rest after ','
    }
  else     // no comma: same value for width, height
    {
      strcpy(str[0], s);
      strcpy(str[1], s);
    }

  // initial patterns if no measurement
  strcpy(pattern[0], "%f");
  strcpy(pattern[1], "%f");

  // look for possible measurement
  factor[0] = factor[1] = -1.0;
  for (j = 0; j < MEASUREMENTS_COUNT; j++)
    {
      for (k = 0; k < 2; k++)
        {
          if ( strstr(str[k], MEASUREMENTS[j][0]) != NULL )
            {
              sscanf(MEASUREMENTS[j][1], "%f", &factor[k]); // convert string to float
              sprintf(pattern[k], "%%f%s", (char*)MEASUREMENTS[j][0]);
            }
        }
    }

  // get values
  f[0] = f[1] = -1.0;
  for (k = 0; k < 2; k++)
    {
      sscanf(str[k], pattern[k], &f[k]);
      if (factor[k] == -1.0)   // direct pixel value
        {
          i[k] = (int)f[k];
        }
      else
        {
          i[k] = (int)(f[k] * factor[k] * dpi);
        }
      if (f[k] == -1.0)
        {
          i[0] = i[1] = 0;
          *exitCode = 1;
          return;
        }
    }
}


/**
 * Parses a color. Currently only "black" and "white".
 */
int parseColor(char* s, int* exitCode)
{
  int j;

  // is s a color name?
  for (j = 0; j < COLORS_COUNT; j++)
    {
      if (strcmp(s, COLORS[j][0])==0)
        {
          if (j == 0)   // simple
            {
              return BLACK;
            }
          else
            {
              return WHITE;
            }
        }
    }
  *exitCode = 1;
  return WHITE;
}


/**
 * Outputs a pair of two integers seperated by a comma.
 */
void printInts(int i[2])
{
  printf("[%d,%d]\n", i[0], i[1]);
}


/**
 * Parses either a single float string, of a pair of two floats seperated
 * by a comma.
 */
void parseFloats(char* s, float f[2])
{
  f[0] = -1.0;
  f[1] = -1.0;
  sscanf(s, "%f,%f", &f[0], &f[1]);
  if (f[1]==-1.0)
    {
      f[1] = f[0]; // if second value is unset, copy first one into
    }
}


/**
 * Outputs a pair of two floats seperated by a comma.
 */
void printFloats(float f[2])
{
  printf("[%f,%f]\n", f[0], f[1]);
}


/**
 * Combines an array of strings to a comma-seperated string.
 */
char* implode(char* buf, char* s[], int cnt)
{
  int i;
  if (cnt > 0)
    {
      if (s[0] != NULL)
        {
          strcpy(buf, s[0]);
        }
      else
        {
          strcpy(buf, BLANK_TEXT);
        }
      for (i = 1; i < cnt; i++)
        {
          if (s[i] != NULL)
            {
              sprintf(buf, "%s, %s", buf, s[i]);
            }
          else
            {
              sprintf(buf, "%s, %s", buf, BLANK_TEXT);
            }
        }
    }
  else
    {
      buf[0] = 0; //strcpy(buf, "");
    }
  return buf;
}

/**
 * Parses a string at argv[*i] argument consisting of comma-concatenated
 * integers. The string may also be of a different format, in which case
 * *i remains unchanged and *multiIndexCount is set to -1.
 *
 * @see isInMultiIndex(..)
 */
void parseMultiIndex(int* i, char* argv[], int multiIndex[], int* multiIndexCount)
{
  char s1[MAX_MULTI_INDEX * 5]; // buffer
  char s2[MAX_MULTI_INDEX * 5]; // buffer
  char c;
  int index;
  int j;

  (*i)++;
  *multiIndexCount = 0;
  if (argv[*i][0] != '-')   // not another option directly following
    {
      strcpy(s1, argv[*i]); // argv[*i] -> s1
      do
        {
          index = -1;
          s2[0] = (char)0; // = ""
          sscanf(s1, "%d%c%s", &index, &c, s2);
          if (index != -1)
            {
              multiIndex[(*multiIndexCount)++] = index;
              if (c=='-')   // range is specified: get range end
                {
                  strcpy(s1, s2); // s2 -> s1
                  sscanf(s1, "%d,%s", &index, s2);
                  for (j = multiIndex[(*multiIndexCount)-1]+1; j <= index; j++)
                    {
                      multiIndex[(*multiIndexCount)++] = j;
                    }
                }
            }
          else
            {
              // string is not correctly parseable: break without inreasing *i (string may be e.g. input-filename)
              *multiIndexCount = -1; // disable all
              (*i)--;
              return; // exit here
            }
          strcpy(s1, s2); // s2 -> s1
        }
      while ((*multiIndexCount < MAX_MULTI_INDEX) && (strlen(s1) > 0));
    }
  else     // no explicit list of sheet-numbers given
    {
      *multiIndexCount = -1; // disable all
      (*i)--;
      return;
    }
}


/**
 * Tests whether an integer is included in the array of integers given as multiIndex.
 * If multiIndexCount is -1, each possible integer is considered to be in the
 * multiIndex list.
 *
 * @see parseMultiIndex(..)
 */
BOOLEAN isInMultiIndex(int index, int multiIndex[MAX_MULTI_INDEX], int multiIndexCount)
{
  int i;

  if (multiIndexCount == -1)
    {
      return TRUE; // all
    }
  else
    {
      for (i = 0; i < multiIndexCount; i++)
        {
          if (index == multiIndex[i])
            {
              return TRUE; // found in list
            }
        }
      return FALSE; // not found in list
    }
}


/**
 * Tests whether 'index' is either part of multiIndex or excludeIndex.
 * (Throughout the application, excludeIndex generalizes several individual
 * multi-indices: if an entry is part of excludeIndex, it is treated as being
 * an entry of all other multiIndices, too.)
 */
BOOLEAN isExcluded(int index, int multiIndex[MAX_MULTI_INDEX], int multiIndexCount, int excludeIndex[MAX_MULTI_INDEX], int excludeIndexCount)
{
  if ( (isInMultiIndex(index, excludeIndex, excludeIndexCount) == TRUE) || (isInMultiIndex(index, multiIndex, multiIndexCount) == TRUE) )
    return(TRUE);
  else
    return(FALSE);
}


/**
 * Outputs all entries in an array of integer to the console.
 */
void printMultiIndex(int multiIndex[MAX_MULTI_INDEX], int multiIndexCount)
{
  int i;

  if (multiIndexCount == -1)
    {
      printf("all");
    }
  else if (multiIndexCount == 0)
    {
      printf("none");
    }
  else
    {
      for (i = 0; i < multiIndexCount; i++)
        {
          printf("%d", multiIndex[i]);
          if (i < multiIndexCount-1)
            {
              printf(",");
            }
        }
    }
  printf("\n");
}


/**
 * Tests if a point is covered by a mask.
 */
BOOLEAN inMask(int x, int y, int mask[EDGES_COUNT])
{
  if ( (x >= mask[LEFT]) && (x <= mask[RIGHT]) && (y >= mask[TOP]) && (y <= mask[BOTTOM]) )
    {
      return TRUE;
    }
  else
    {
      return FALSE;
    }
}


/**
 * Tests if masks a and b overlap.
 */
BOOLEAN masksOverlap(int a[EDGES_COUNT], int b[EDGES_COUNT])
{
  if ( inMask(a[LEFT], a[TOP], b) || inMask(a[RIGHT], a[BOTTOM], b) )
    return(TRUE);
  else
    return(FALSE);
}


/**
 * Tests if at least one mask in masks overlaps with m.
 */
BOOLEAN masksOverlapAny(int m[EDGES_COUNT], int masks[MAX_MASKS][EDGES_COUNT], int masksCount)
{
  int i;

  for ( i = 0; i < masksCount; i++ )
    {
      if ( masksOverlap(m, masks[i]) )
        {
          return TRUE;
        }
    }
  return FALSE;
}


/* --- tool functions for image handling ---------------------------------- */

/**
 * Allocates a memory block for storing image data and fills the IMAGE-struct
 * with the specified values.
 */
void initImage(struct IMAGE* image, int width, int height, int bitdepth, BOOLEAN color, int background)
{
  int size;

  size = width * height;
  if ( color )
    {
      image->bufferGrayscale = (unsigned char*)malloc(size);
      image->bufferLightness = (unsigned char*)malloc(size);
      image->bufferDarknessInverse = (unsigned char*)malloc(size);
      memset(image->bufferGrayscale, background, size);
      memset(image->bufferLightness, background, size);
      memset(image->bufferDarknessInverse, background, size);
      size *= 3;
    }
  image->buffer = (unsigned char*)malloc(size);
  memset(image->buffer, background, size);
  if ( ! color )
    {
      image->bufferGrayscale = image->buffer;
      image->bufferLightness = image->buffer;
      image->bufferDarknessInverse = image->buffer;
    }
  image->width = width;
  image->height = height;
  image->bitdepth = bitdepth;
  image->color = color;
  image->background = background;
}


/**
 * Frees an image.
 */
void freeImage(struct IMAGE* image)
{
  free(image->buffer);
  if (image->color)
    {
      free(image->bufferGrayscale);
      free(image->bufferLightness);
      free(image->bufferDarknessInverse);
    }
}


/**
 * Replaces one image with another.
 */
void replaceImage(struct IMAGE* image, struct IMAGE* newimage)
{
  freeImage(image);
  // pass-back new image
  *image = *newimage; // copy whole struct
}


/**
 * Sets the color/grayscale value of a single pixel.
 *
 * @return TRUE if the pixel has been changed, FALSE if the original color was the one to set
 */
BOOLEAN setPixel(int pixel, int x, int y, struct IMAGE* image)
{
  unsigned char* p;
  int w, h;
  int pos;
  BOOLEAN result;
  unsigned char r, g, b;

  w = image->width;
  h = image->height;
  if ( (x < 0) || (x >= w) || (y < 0) || (y >= h) )
    {
      return FALSE; //nop
    }
  else
    {
      pos = (y * w) + x;
      r = (pixel >> 16) & 0xff;
      g = (pixel >> 8) & 0xff;
      b = pixel & 0xff;
      if ( ! image->color )
        {
          p = &image->buffer[pos];
          if ((r == g) && (r == b))   // optimization (avoid division by 3)
            {
              pixel = r;
            }
          else
            {
              pixel = pixelGrayscale(r, g, b); // convert to gray (will already be in most cases, but we can't be sure)
            }
          if (*p != (unsigned char)pixel)
            {
              *p = (unsigned char)pixel;
              return TRUE;
            }
          else
            {
              return FALSE;
            }
        }
      else     // color
        {
          result = FALSE;
          p = &image->buffer[pos*3];
          if (*p != r)
            {
              *p = r;
              result = TRUE;
            }
          p++;
          if (*p != g)
            {
              *p = g;
              result = TRUE;
            }
          p++;
          if (*p != b)
            {
              *p = b;
              result = TRUE;
            }
          if ( result )   // modified: update cached grayscale, lightness and brightnessInverse values
            {
              image->bufferGrayscale[pos] = pixelGrayscale(r, g, b);
              image->bufferLightness[pos] = pixelLightness(r, g, b);
              image->bufferDarknessInverse[pos] = pixelDarknessInverse(r, g, b);
            }
          return result;
        }
    }
}


/**
 * Returns the color or grayscale value of a single pixel.
 * Always returns a color-compatible value (which may be interpreted as 8-bit grayscale)
 *
 * @return color or grayscale-value of the requested pixel, or WHITE if the coordinates are outside the image
 */
int getPixel(int x, int y, struct IMAGE* image)
{
  int w, h;
  int pos;
  int pix;
  unsigned char r, g, b;

  w = image->width;
  h = image->height;
  if ( (x < 0) || (x >= w) || (y < 0) || (y >= h) )
    {
      return pixelValue(WHITE, WHITE, WHITE);
    }
  else
    {
      pos = (y * w) + x;
      if ( ! image->color )
        {
          pix = (unsigned char)image->buffer[pos];
          return pixelValue(pix, pix, pix);
        }
      else     // color
        {
          pos *= 3;
          r = (unsigned char)image->buffer[pos++];
          g = (unsigned char)image->buffer[pos++];
          b = (unsigned char)image->buffer[pos];
          return pixelValue(r, g, b);
        }
    }
}


/**
 * Returns a color component of a single pixel (0-255).
 *
 * @param colorComponent either RED, GREEN or BLUE
 * @return color or grayscale-value of the requested pixel, or WHITE if the coordinates are outside the image
 */
/* (currently not used)
int getPixelComponent(int x, int y, int colorComponent, struct IMAGE* image) {
   int w, h;
   int pos;

   w = image->width;
   h = image->height;
   if ( (x < 0) || (x >= w) || (y < 0) || (y >= h) ) {
       return WHITE;
   } else {
       pos = (y * w) + x;
       if ( ! image->color ) {
           return (unsigned char)image->buffer[pos];
       } else { // color
           return (unsigned char)image->buffer[(pos * 3) + colorComponent];
       }
   }
}
*/

/**
 * Returns the grayscale (=brightness) value of a single pixel.
 *
 * @return grayscale-value of the requested pixel, or WHITE if the coordinates are outside the image
 */
int getPixelGrayscale(int x, int y, struct IMAGE* image)
{
  int w, h;
  int pos;

  w = image->width;
  h = image->height;
  if ( (x < 0) || (x >= w) || (y < 0) || (y >= h) )
    {
      return WHITE;
    }
  else
    {
      pos = (y * w) + x;
      return image->bufferGrayscale[pos];
    }
}


/**
 * Returns the 'lightness' value of a single pixel. For color images, this
 * value denotes the minimum brightness of a single color-component in the
 * total color, which means that any color is considered 'dark' which has
 * either the red, the green or the blue component (or, of course, several
 * of them) set to a high value. In some way, this is a measure how close a
 * color is to white.
 * For grayscale images, this value is equal to the pixel brightness.
 *
 * @return lightness-value (the higher, the lighter) of the requested pixel, or WHITE if the coordinates are outside the image
 */
int getPixelLightness(int x, int y, struct IMAGE* image)
{
  int w, h;
  int pos;

  w = image->width;
  h = image->height;
  if ( (x < 0) || (x >= w) || (y < 0) || (y >= h) )
    {
      return WHITE;
    }
  else
    {
      pos = (y * w) + x;
      return image->bufferLightness[pos];
    }
}


/**
 * Returns the 'inverse-darkness' value of a single pixel. For color images, this
 * value denotes the maximum brightness of a single color-component in the
 * total color, which means that any color is considered 'light' which has
 * either the red, the green or the blue component (or, of course, several
 * of them) set to a high value. In some way, this is a measure how far away a
 * color is to black.
 * For grayscale images, this value is equal to the pixel brightness.
 *
 * @return inverse-darkness-value (the LOWER, the darker) of the requested pixel, or WHITE if the coordinates are outside the image
 */
int getPixelDarknessInverse(int x, int y, struct IMAGE* image)
{
  int w, h;
  int pos;

  w = image->width;
  h = image->height;
  if ( (x < 0) || (x >= w) || (y < 0) || (y >= h) )
    {
      return WHITE;
    }
  else
    {
      pos = (y * w) + x;
      return image->bufferDarknessInverse[pos];
    }
}


/**
 * Sets the color/grayscale value of a single pixel to either black or white.
 *
 * @return TRUE if the pixel has been changed, FALSE if the original color was the one to set
 */
BOOLEAN setPixelBW(int x, int y, struct IMAGE* image, int blackwhite)
{
  unsigned char* p;
  int w, h;
  int pos;
  BOOLEAN result;

  w = image->width;
  h = image->height;
  if ( (x < 0) || (x >= w) || (y < 0) || (y >= h) )
    {
      return FALSE; //nop
    }
  else
    {
      pos = (y * w) + x;
      if ( ! image->color )
        {
          p = &image->buffer[pos];
          if (*p != blackwhite)
            {
              *p = blackwhite;
              return TRUE;
            }
          else
            {
              return FALSE;
            }
        }
      else     // color
        {
          p = &image->buffer[pos * 3];
          result = FALSE;
          if (*p != blackwhite)
            {
              *p = blackwhite;
              result = TRUE;
            }
          p++;
          if (*p != blackwhite)
            {
              *p = blackwhite;
              result = TRUE;
            }
          p++;
          if (*p != blackwhite)
            {
              *p = blackwhite;
              result = TRUE;
            }
          return result;
        }
    }
}


/**
 * Sets the color/grayscale value of a single pixel to white.
 *
 * @return TRUE if the pixel has been changed, FALSE if the original color was the one to set
 */
BOOLEAN clearPixel(int x, int y, struct IMAGE* image)
{
  return setPixelBW(x, y, image, WHITE);
}


/**
 * Clears a rectangular area of pixels with either black or white.
 * @return The number of pixels actually changed from black (dark) to white.
 */
int clearRect(int left, int top, int right, int bottom, struct IMAGE* image, int blackwhite)
{
  int x;
  int y;
  int count;

  count = 0;
  for (y = top; y <= bottom; y++)
    {
      for (x = left; x <= right; x++)
        {
          if (setPixelBW(x, y, image, blackwhite))
            {
              count++;
            }
        }
    }
  return count;
}


/**
 * Copies one area of an image into another.
 */
void copyImageArea(int x, int y, int width, int height, struct IMAGE* source, int toX, int toY, struct IMAGE* target)
{
  int row;
  int col;
  int pixel;
  // naive but generic implementation
  for (row = 0; row < height; row++)
    {
      for (col = 0; col < width; col++)
        {
          pixel = getPixel(x+col, y+row, source);
          setPixel(pixel, toX+col, toY+row, target);
        }
    }
}


/**
 * Copies a whole image into another.
 */
void copyImage(struct IMAGE* source, int toX, int toY, struct IMAGE* target)
{
  copyImageArea(0, 0, source->width, source->height, source, toX, toY, target);
}


/**
 * Centers one area of an image inside an area of another image.
 * If the source area is smaller than the target area, is is equally
 * surrounded by a white border, if it is bigger, it gets equally cropped
 * at the edges.
 */
void centerImageArea(int x, int y, int w, int h, struct IMAGE* source, int toX, int toY, int ww, int hh, struct IMAGE* target)
{
  if ((w < ww) || (h < hh))   // white rest-border will remain, so clear first
    {
      clearRect(toX, toY, toX + ww - 1, toY + hh - 1, target, target->background);
    }
  if (w < ww)
    {
      toX += (ww - w) / 2;
    }
  if (h < hh)
    {
      toY += (hh - h) / 2;
    }
  if (w > ww)
    {
      x += (w - ww) / 2;
      w = ww;
    }
  if (h > hh)
    {
      y += (h - hh) / 2;
      h = hh;
    }
  copyImageArea(x, y, w, h, source, toX, toY, target);
}


/**
 * Centers a whole image inside an area of another image.
 */
void centerImage(struct IMAGE* source, int toX, int toY, int ww, int hh, struct IMAGE* target)
{
  centerImageArea(0, 0, source->width, source->height, source, toX, toY, ww, hh, target);
}


/**
 * Returns the average brightness of a rectagular area.
 */
int brightnessRect(int x1, int y1, int x2, int y2, struct IMAGE* image)
{
  int x;
  int y;
  int pixel;
  int total;
  int count;
  total = 0;
  count = (x2-x1+1)*(y2-y1+1);
  for (x = x1; x <= x2; x++)
    {
      for (y = y1; y <= y2; y++)
        {
          pixel = getPixelGrayscale(x, y, image);
          total += pixel;
        }
    }
  return total / count;
}


/**
 * Returns the average lightness of a rectagular area.
 */
int lightnessRect(int x1, int y1, int x2, int y2, struct IMAGE* image)
{
  int x;
  int y;
  int pixel;
  int total;
  int count;
  total = 0;
  count = (x2-x1+1)*(y2-y1+1);
  for (x = x1; x <= x2; x++)
    {
      for (y = y1; y <= y2; y++)
        {
          pixel = getPixelLightness(x, y, image);
          total += pixel;
        }
    }
  return total / count;
}


/**
 * Returns the average darkness of a rectagular area.
 */
int darknessInverseRect(int x1, int y1, int x2, int y2, struct IMAGE* image)
{
  int x;
  int y;
  int pixel;
  int total;
  int count;
  total = 0;
  count = (x2-x1+1)*(y2-y1+1);
  for (x = x1; x <= x2; x++)
    {
      for (y = y1; y <= y2; y++)
        {
          pixel = getPixelDarknessInverse(x, y, image);
          total += pixel;
        }
    }
  return total / count;
}


/**
 * Counts the number of pixels in a rectangular area whose grayscale
 * values ranges between minColor and maxBrightness. Optionally, the area can get
 * cleared with white color while counting.
 */
int countPixelsRect(int left, int top, int right, int bottom, int minColor, int maxBrightness, BOOLEAN clear, struct IMAGE* image)
{
  int x;
  int y;
  int pixel;
  int count;

  count = 0;
  for (y = top; y <= bottom; y++)
    {
      for (x = left; x <= right; x++)
        {
          pixel = getPixelGrayscale(x, y, image);
          if ((pixel>=minColor) && (pixel <= maxBrightness))
            {
              if (clear==TRUE)
                {
                  clearPixel(x, y, image);
                }
              count++;
            }
        }
    }
  return count;
}


/**
 * Counts the number of dark pixels around the pixel at (x,y), who have a
 * square-metric distance of 'level' to the (x,y) (thus, testing the values
 * of 9 pixels if level==1, 16 pixels if level==2 and so on).
 * Optionally, the pixels can get cleared after counting.
 */
int countPixelNeighborsLevel(int x, int y, BOOLEAN clear, int level, int whiteMin, struct IMAGE* image)
{
  int xx;
  int yy;
  int count;
  int pixel;

  count = 0;
  // upper and lower rows
  for (xx = x - level; xx <= x + level; xx++)
    {
      // upper row
      pixel = getPixelLightness(xx, y - level, image);
      if (pixel < whiteMin)   // non-light pixel found
        {
          if (clear == TRUE)
            {
              clearPixel(xx, y - level, image);
            }
          count++;
        }
      // lower row
      pixel = getPixelLightness(xx, y + level, image);
      if (pixel < whiteMin)
        {
          if (clear == TRUE)
            {
              clearPixel(xx, y + level, image);
            }
          count++;
        }
    }
  // middle rows
  for (yy = y-(level-1); yy <= y+(level-1); yy++)
    {
      // first col
      pixel = getPixelLightness(x - level, yy, image);
      if (pixel < whiteMin)
        {
          if (clear == TRUE)
            {
              clearPixel(x - level, yy, image);
            }
          count++;
        }
      // last col
      pixel = getPixelLightness(x + level, yy, image);
      if (pixel < whiteMin)
        {
          if (clear == TRUE)
            {
              clearPixel(x + level, yy, image);
            }
          count++;
        }
    }
  /* old version, not optimized:
  for (yy = y-level; yy <= y+level; yy++) {
      for (xx = x-level; xx <= x+level; xx++) {
          if (abs(xx-x)==level || abs(yy-y)==level) {
              pixel = getPixelLightness(xx, yy, image);
              if (pixel < whiteMin) {
                  if (clear==TRUE) {
                      clearPixel(xx, yy, image);
                  }
                  count++;
              }
          }
      }
  }*/
  return count;
}


/**
 * Count all dark pixels in the distance 0..intensity that are directly
 * reachable from the dark pixel at (x,y), without having to cross bright
 * pixels.
 */
int countPixelNeighbors(int x, int y, int intensity, int whiteMin, struct IMAGE* image)
{
  int level;
  int count;
  int lCount;

  count = 1; // assume self as set
  lCount = -1;
  for (level = 1; (lCount != 0) && (level <= intensity); level++)   // can finish when one level is completely zero
    {
      lCount = countPixelNeighborsLevel(x, y, FALSE, level, whiteMin, image);
      count += lCount;
    }
  return count;
}


/**
 * Clears all dark pixels that are directly reachable from the dark pixel at
 * (x,y). This should be called only if it has previously been detected that
 * the amount of pixels to clear will be reasonable small.
 */
void clearPixelNeighbors(int x, int y, int whiteMin, struct IMAGE* image)
{
  int level;
  int lCount;

  clearPixel(x, y, image);
  lCount = -1;
  for (level = 1; lCount != 0; level++)   // lCount will become 0, otherwise countPixelNeighbors() would previously have delivered a bigger value (and this here would not have been called)
    {
      lCount = countPixelNeighborsLevel(x, y, TRUE, level, whiteMin, image);
    }
}


/**
 * Flood-fill an area of pixels.
 * (Declaration of header for indirect recursive calls.)
 */
void floodFill(int x, int y, int color, int maskMin, int maskMax, int intensity, struct IMAGE* image);


/**
 * Solidly fills a line of pixels heading towards a specified direction
 * until color-changes in the pixels to overwrite exceed the 'intensity'
 * parameter.
 *
 * @param stepX either -1 or 1, if stepY is 0, else 0
 * @param stepY either -1 or 1, if stepX is 0, else 0
 */
int fillLine(int x, int y, int stepX, int stepY, int color, int maskMin, int maskMax, int intensity, struct IMAGE* image)
{
  int pixel;
  int distance;
  int intensityCount;
  int w, h;

  w = image->width;
  h = image->height;
  distance = 0;
  intensityCount = 1; // first pixel must match, otherwise directly exit
  while (1==1)   // !
    {
      x += stepX;
      y += stepY;
      pixel = getPixelGrayscale(x, y, image);
      if ((pixel>=maskMin) && (pixel<=maskMax))
        {
          intensityCount = intensity; // reset counter
        }
      else
        {
          intensityCount--; // allow maximum of 'intensity' pixels to be bright, until stop
        }
      if ((intensityCount > 0) && (x>=0) && (x<w) && (y>=0) && (y<h))
        {
          setPixel(color, x, y, image);
          distance++;
        }
      else
        {
          return distance; // exit here
        }
    }
}


/**
 * Start flood-filling around the edges of a line which has previously been
 * filled using fillLine(). Here, the flood-fill algorithm performs its
 * indirect recursion.
 *
 * @param stepX either -1 or 1, if stepY is 0, else 0
 * @param stepY either -1 or 1, if stepX is 0, else 0
 * @see fillLine()
 */
void floodFillAroundLine(int x, int y, int stepX, int stepY, int distance, int color, int maskMin, int maskMax, int intensity, struct IMAGE* image)
{
  int d;

  for (d = 0; d < distance; d++)
    {
      if (stepX != 0)
        {
          x += stepX;
          floodFill(x, y + 1, color, maskMin, maskMax, intensity, image); // indirect recursion
          floodFill(x, y - 1, color, maskMin, maskMax, intensity, image); // indirect recursion
        }
      else     // stepY != 0
        {
          y += stepY;
          floodFill(x + 1, y, color, maskMin, maskMax, intensity, image); // indirect recursion
          floodFill(x - 1, y, color, maskMin, maskMax, intensity, image); // indirect recursion
        }
    }
}


/**
 * Flood-fill an area of pixels. (Naive implementation, optimizable.)
 *
 * @see earlier header-declaration to enable indirect recursive calls
 */
void floodFill(int x, int y, int color, int maskMin, int maskMax, int intensity, struct IMAGE* image)
{
  int left;
  int top;
  int right;
  int bottom;
  int pixel;

  // is current pixel to be filled?
  pixel = getPixelGrayscale(x, y, image);
  if ((pixel>=maskMin) && (pixel<=maskMax))
    {
      // first, fill a 'cross' (both vertical, horizontal line)
      setPixel(color, x, y, image);
      left = fillLine(x, y, -1, 0, color, maskMin, maskMax, intensity, image);
      top = fillLine(x, y, 0, -1, color, maskMin, maskMax, intensity, image);
      right = fillLine(x, y, 1, 0, color, maskMin, maskMax, intensity, image);
      bottom = fillLine(x, y, 0, 1, color, maskMin, maskMax, intensity, image);
      // now recurse on each neighborhood-pixel of the cross (most recursions will immediately return)
      floodFillAroundLine(x, y, -1, 0, left, color, maskMin, maskMax, intensity, image);
      floodFillAroundLine(x, y, 0, -1, top, color, maskMin, maskMax, intensity, image);
      floodFillAroundLine(x, y, 1, 0, right, color, maskMin, maskMax, intensity, image);
      floodFillAroundLine(x, y, 0, 1, bottom, color, maskMin, maskMax, intensity, image);
    }
}



/* --- tool function for file handling ------------------------------------ */

/**
 * Tests if a file exists.
 */
BOOLEAN fileExists(const char* filename)
{
  FILE *f;
  f = fopen(filename,"r");
  if (f == NULL)
    {
      return FALSE;
    }
  else
    {
      fclose(f);
      return TRUE;
    }
}


/**
 * Loads image data from a file in pnm format.
 *
 * @param filename name of file to load
 * @param image structure to hold loaded image
 * @param type returns the type of the loaded image
 * @return TRUE on success, FALSE on failure
 */
BOOLEAN loadImage(char* filename, struct IMAGE* image, int* type)
{
  FILE *f;
  int fileSize;
  int bytesPerLine;
  char magic[10];
  char word[255];
  char c;
  int maxColorIndex;
  int inputSize;
  int inputSizeFile;
  int read;
  unsigned char* buffer2;
  int lineOffsetInput;
  int lineOffsetOutput;
  int x;
  int y;
  int bb;
  int off;
  int bits;
  int bit;
  int pixel;
  int size;
  int pos;
  unsigned char* p;
  unsigned char r, g, b;


  // open input file
  f = fopen(filename, "rb");
  if (f == NULL)
    {
      return FALSE;
    }

  // get file size
  fseek(f, 0, SEEK_END); // to end
  fileSize = ftell(f);
  rewind(f); // back to start

  // read magic number
  fread(magic, 1, 2, f);
  magic[2] = 0; // terminate
  if (strcmp(magic, "P4")==0)
    {
      *type = PBM;
      image->bitdepth = 1;
      image->color = FALSE;
    }
  else if (strcmp(magic, "P5")==0)
    {
      *type = PGM;
      image->bitdepth = 8;
      image->color = FALSE;
    }
  else if (strcmp(magic, "P6")==0)
    {
      *type = PPM;
      image->bitdepth = 8;
      image->color = TRUE;
    }
  else
    {
      return FALSE;
    }

  // get image info: width, height, optionally depth
  fgetc(f); // skip \n after magic number
  fscanf(f, "%s", word);
  while (word[0]=='#')   // skip comment lines
    {
      do
        {
          fscanf(f, "%c", &c);
        }
      while ((feof(f)==0)&&(c!='\n'));
      fscanf(f, "%s", word);
    }
  // now reached width/height pair as decimal ascii
  sscanf(word, "%d", &image->width);
  fscanf(f, "%d", &image->height);
  fgetc(f); // skip \n after width/height pair
  if (*type == PBM)
    {
      bytesPerLine = (image->width + 7) / 8;
    }
  else     // PGM or PPM
    {
      fscanf(f, "%s", word);
      while (word[0]=='#')   // skip comment lines
        {
          do
            {
              fscanf(f, "%c", &c);
            }
          while ((feof(f) == 0) && (c != '\n'));
          fscanf(f, "%s", word);
        }
      // read max color value
      sscanf(word, "%d", &maxColorIndex);
      fgetc(f); // skip \n after max color index
      if (maxColorIndex > 255)
        {
          return FALSE;
        }
      bytesPerLine = image->width;
      if (*type == PPM)
        {
          bytesPerLine *= 3; // 3 color-components per pixel
        }
    }

  // read binary image data
  inputSizeFile = fileSize - ftell(f);
  inputSize = bytesPerLine * image->height;

  image->buffer = (unsigned char*)malloc(inputSize);
  read = fread(image->buffer, 1, inputSize, f);
  if (read != inputSize)
    {
      return FALSE;
    }

  if (*type == PBM)   // internally convert b&w to 8-bit for processing
    {
      buffer2 = (unsigned char*)malloc(image->width * image->height);
      lineOffsetInput = 0;
      lineOffsetOutput = 0;
      for (y = 0; y < image->height; y++)
        {
          for (x = 0; x < image->width; x++)
            {
              bb = x >> 3;  // x / 8;
              off = x & 7; // x % 8;
              bit = 128>>off;
              bits = image->buffer[lineOffsetInput + bb];
              bits &= bit;
              if (bits == 0)   // 0: white pixel
                {
                  pixel = 0xff;
                }
              else
                {
                  pixel = 0x00;
                }
              buffer2[lineOffsetOutput+x] = pixel; // set as whole byte
            }
          lineOffsetInput += bytesPerLine;
          lineOffsetOutput += image->width;
        }
      free(image->buffer);
      image->buffer = buffer2;
    }
  fclose(f);

  if (*type == PPM)
    {
      // init cached values for grayscale, lightness and darknessInverse
      size = image->width * image->height;
      image->bufferGrayscale = (unsigned char*)malloc(size);
      image->bufferLightness = (unsigned char*)malloc(size);
      image->bufferDarknessInverse = (unsigned char*)malloc(size);
      p = image->buffer;
      for (pos = 0; pos < size; pos++)
        {
          r = *p;
          p++;
          g = *p;
          p++;
          b = *p;
          p++;
          image->bufferGrayscale[pos] = pixelGrayscale(r, g, b);
          image->bufferLightness[pos] = pixelLightness(r, g, b);
          image->bufferDarknessInverse[pos] = pixelDarknessInverse(r, g, b);
        }
    }
  else
    {
      image->bufferGrayscale = image->buffer;
      image->bufferLightness = image->buffer;
      image->bufferDarknessInverse = image->buffer;
    }

  return TRUE;
}


/**
 * Saves image data to a file in pgm or pbm format.
 *
 * @param filename name of file to save
 * @param image image to save
 * @param type filetype of the image to save
 * @param overwrite allow overwriting existing files
 * @param blackThreshold threshold for grayscale-to-black&white conversion
 * @return TRUE on success, FALSE on failure
 */
BOOLEAN saveImage(const char* filename, struct IMAGE* image, int type, BOOLEAN overwrite, float blackThreshold)
{
  unsigned char* buf;
  int bytesPerLine;
  int inputSize;
  int outputSize;
  int lineOffsetOutput;
  int offsetInput;
  int offsetOutput;
  int x;
  int y;
  int pixel;
  int b;
  int off;
  unsigned char bit;
  unsigned char val;
  const char* outputMagic;
  FILE* outputFile;
  int blackThresholdAbs;
  BOOLEAN result;


  result = TRUE;
  if (type == PBM)   // convert to pbm
    {
      blackThresholdAbs = WHITE * (1.0 - blackThreshold);
      bytesPerLine = (image->width + 7) >> 3; // / 8;
      outputSize = bytesPerLine * image->height;
      buf = (unsigned char*)malloc(outputSize);
      memset(buf, 0, outputSize);
      lineOffsetOutput = 0;
      for (y = 0; y < image->height; y++)
        {
          for (x = 0; x < image->width; x++)
            {
              pixel = getPixelGrayscale(x, y, image);
              b = x >> 3; // / 8;
              off = x & 7; // % 8;
              bit = 128>>off;
              val = buf[lineOffsetOutput + b];
              if (pixel < blackThresholdAbs)   // dark
                {
                  val |= bit; // set bit to one: black
                }
              else     // bright
                {
                  val &= (~bit); // set bit to zero: white
                }
              buf[lineOffsetOutput+b] = val;
            }
          lineOffsetOutput += bytesPerLine;
        }
    }
  else if (type == PPM)     // maybe convert to color
    {
      outputSize = image->width * image->height * 3;
      if (image->color)   // color already
        {
          buf = image->buffer;
        }
      else     // convert to color
        {
          buf = (unsigned char*)malloc(outputSize);
          inputSize = image->width * image->height;
          offsetOutput = 0;
          for (offsetInput = 0; offsetInput < inputSize; offsetInput++)
            {
              pixel = image->buffer[offsetInput];
              buf[offsetOutput++] = pixel;
              buf[offsetOutput++] = pixel;
              buf[offsetOutput++] = pixel;
            }
        }
    }
  else     // PGM
    {
      outputSize = image->width * image->height;
      buf = image->buffer;
    }

  switch (type)
    {
    case PBM:
      outputMagic = "P4";
      break;
    case PPM:
      outputMagic = "P6";
      break;
    default: // PGM
      outputMagic = "P5";
      break;
    }

  // write to file
  if ( overwrite || ( ! fileExists( filename ) ) )
    {
      outputFile = fopen(filename, "wb");
      if (outputFile != 0)
        {
          fprintf(outputFile, "%s\n", outputMagic);
          fprintf(outputFile, "# generated by unpaper\n");
          fprintf(outputFile, "%u %u\n", image->width, image->height);
          if ((type == PGM)||(type == PPM))
            {
              fprintf(outputFile, "255\n"); // maximum color index per color-component
            }
          fwrite(buf, 1, outputSize, outputFile);
          fclose(outputFile);
        }
      else
        {
          printf("*** error: Cannot open output file '%s'.\n", filename);
          result = FALSE;
        }
    }
  else
    {
      printf("file %s already exists (use --overwrite to replace).\n", filename);
      result = FALSE;
    }
  if (buf != image->buffer)
    {
      free(buf);
    }
  return result;
}


/**
 * Saves the image if full debugging mode is enabled.
 */
void saveDebug(const char* filename, struct IMAGE* image)
{
  int type;

  if (verbose >= VERBOSE_DEBUG_SAVE)
    {
      if (image->color)
        {
          type = PPM;
        }
      else if (image->bitdepth == 1)
        {
          type = PBM;
        }
      else
        {
          type = PGM;
        }
      saveImage(filename, image, type, TRUE, 0.5); // 0.5 is a dummy, not used because PGM depth
    }
}



/****************************************************************************
 * image processing functions                                               *
 ****************************************************************************/


/* --- deskewing ---------------------------------------------------------- */

/**
 * Returns the maximum peak value that occurs when shifting a rotated virtual line above the image,
 * starting from one edge of an area and moving towards the middle point of the area.
 * The peak value is calulated by the absolute difference in the average blackness of pixels that occurs between two single shifting steps.
 *
 * @param m ascending slope of the virtually shifted (m=tan(angle)). Mind that this is negative for negative radians.
 */
int detectEdgeRotationPeak(double m, int deskewScanSize, float deskewScanDepth, int shiftX, int shiftY, int left, int top, int right, int bottom, struct IMAGE* image)
{
  int width;
  int height;
  int mid;
  int half;
  int sideOffset;
  int outerOffset;
  double X; // unrounded coordinates
  double Y;
  double stepX;
  double stepY;
  int x[MAX_ROTATION_SCAN_SIZE];
  int y[MAX_ROTATION_SCAN_SIZE];
  int xx;
  int yy;
  int lineStep;
  int dep;
  int pixel;
  int blackness;
  int lastBlackness;
  int diff;
  int maxDiff;
  int maxBlacknessAbs;
  int maxDepth;
  int accumulatedBlackness;

  width = right-left+1;
  height = bottom-top+1;
  maxBlacknessAbs = (int) 255 * deskewScanSize * deskewScanDepth;

  if (shiftY==0)   // horizontal detection
    {
      if (deskewScanSize == -1)
        {
          deskewScanSize = height;
        }
      limit(&deskewScanSize, MAX_ROTATION_SCAN_SIZE);
      limit(&deskewScanSize, height);

      maxDepth = width/2;
      half = deskewScanSize/2;
      outerOffset = (int)(abs(m) * half);
      mid = height/2;
      sideOffset = shiftX > 0 ? left-outerOffset : right+outerOffset;
      X = sideOffset + half * m;
      Y = top + mid - half;
      stepX = -m;
      stepY = 1.0;
    }
  else     // vertical detection
    {
      if (deskewScanSize == -1)
        {
          deskewScanSize = width;
        }
      limit(&deskewScanSize, MAX_ROTATION_SCAN_SIZE);
      limit(&deskewScanSize, width);
      maxDepth = height/2;
      half = deskewScanSize/2;
      outerOffset = (int)(abs(m) * half);
      mid = width/2;
      sideOffset = shiftY > 0 ? top-outerOffset : bottom+outerOffset;
      X = left + mid - half;
      Y = sideOffset - (half * m);
      stepX = 1.0;
      stepY = -m; // (line goes upwards for negative degrees)
    }

  // fill buffer with coordinates for rotated line in first unshifted position
  for (lineStep = 0; lineStep < deskewScanSize; lineStep++)
    {
      x[lineStep] = (int)X;
      y[lineStep] = (int)Y;
      X += stepX;
      Y += stepY;
    }

  // now scan for edge, modify coordinates in buffer to shift line into search direction (towards the middle point of the area)
  // stop either when detectMaxDepth steps are shifted, or when diff falls back to less than detectThreshold*maxDiff
  lastBlackness = 0;
  diff = 0;
  maxDiff = 0;
  accumulatedBlackness = 0;
  for (dep = 0; (accumulatedBlackness < maxBlacknessAbs) && (dep < maxDepth) ; dep++)
    {
      // calculate blackness of virtual line
      blackness = 0;
      for (lineStep = 0; lineStep < deskewScanSize; lineStep++)
        {
          xx = x[lineStep];
          x[lineStep] += shiftX;
          yy = y[lineStep];
          y[lineStep] += shiftY;
          if ((xx >= left) && (xx <= right) && (yy >= top) && (yy <= bottom))
            {
              pixel = getPixelDarknessInverse(xx, yy, image);
              blackness += (255 - pixel);
            }
        }
      diff = blackness - lastBlackness;
      lastBlackness = blackness;
      if (diff >= maxDiff)
        {
          maxDiff = diff;
        }
      accumulatedBlackness += blackness;
    }
  if (dep < maxDepth)   // has not terminated only because middle was reached
    {
      return maxDiff;
    }
  else
    {
      return 0;
    }
}


/**
 * Detects rotation at one edge of the area specified by left, top, right, bottom.
 * Which of the four edges to take depends on whether shiftX or shiftY is non-zero,
 * and what sign this shifting value has.
 */
double detectEdgeRotation(float deskewScanRange, float deskewScanStep, int deskewScanSize, float deskewScanDepth, int shiftX, int shiftY, int left, int top, int right, int bottom, struct IMAGE* image)
{
  // either shiftX or shiftY is 0, the other value is -i|+i
  // depending on shiftX/shiftY the start edge for shifting is determined
  double rangeRad;
  double stepRad;
  double rotation;
  int peak;
  int maxPeak;
  double detectedRotation;
  double m;

  rangeRad = degreesToRadians((double)deskewScanRange);
  stepRad = degreesToRadians((double)deskewScanStep);
  detectedRotation = 0.0;
  maxPeak = 0;
  // iteratively increase test angle,  alterating between +/- sign while increasing absolute value
  for (rotation = 0.0; rotation <= rangeRad; rotation = (rotation>=0.0) ? -(rotation + stepRad) : -rotation )
    {
      m = tan(rotation);
      peak = detectEdgeRotationPeak(m, deskewScanSize, deskewScanDepth, shiftX, shiftY, left, top, right, bottom, image);
      if (peak > maxPeak)
        {
          detectedRotation = rotation;
          maxPeak = peak;
        }
    }
  return radiansToDegrees(detectedRotation);
}


/**
 * Detect rotation of a whole area.
 * Angles between -deskewScanRange and +deskewScanRange are scanned, at either the
 * horizontal or vertical edges of the area specified by left, top, right, bottom.
 */
double detectRotation(int deskewScanEdges, int deskewScanRange, float deskewScanStep, int deskewScanSize, float deskewScanDepth, float deskewScanDeviation, int left, int top, int right, int bottom, struct IMAGE* image)
{
  double rotation[4];
  int count;
  double total;
  double average;
  double deviation;
  int i;

  count = 0;

  if ((deskewScanEdges & 1<<LEFT) != 0)
    {
      // left
      rotation[count] = detectEdgeRotation(deskewScanRange, deskewScanStep, deskewScanSize, deskewScanDepth, 1, 0, left, top, right, bottom, image);
      count++;
    }
  if ((deskewScanEdges & 1<<TOP) != 0)
    {
      // top
      rotation[count] = - detectEdgeRotation(deskewScanRange, deskewScanStep, deskewScanSize, deskewScanDepth, 0, 1, left, top, right, bottom, image);
      count++;
    }
  if ((deskewScanEdges & 1<<RIGHT) != 0)
    {
      // right
      rotation[count] = detectEdgeRotation(deskewScanRange, deskewScanStep, deskewScanSize, deskewScanDepth, -1, 0, left, top, right, bottom, image);
      count++;
    }
  if ((deskewScanEdges & 1<<BOTTOM) != 0)
    {
      // bottom
      rotation[count] = - detectEdgeRotation(deskewScanRange, deskewScanStep, deskewScanSize, deskewScanDepth, 0, -1, left, top, right, bottom, image);
      count++;
    }

  total = 0.0;
  for (i = 0; i < count; i++)
    {
      total += rotation[i];
    }
  average = total / count;
  total = 0.0;
  for (i = 0; i < count; i++)
    {
      total += sqr(rotation[i]-average);
    }
  deviation = sqrt(total);
  if (deviation <= deskewScanDeviation)
    {
      return average;
    }
  else
    {
      return 0.0;
    }
}


/**
 * Rotates a whole image buffer by the specified radians, around its middle-point.
 * Usually, the buffer should have been converted to a qpixels-representation before, to increase quality.
 * (To rotate parts of an image, extract the part with copyBuffer, rotate, and re-paste with copyBuffer.)
 */
//void rotate(double radians, struct IMAGE* source, struct IMAGE* target, double* trigonometryCache, int trigonometryCacheBaseSize) {
void rotate(double radians, struct IMAGE* source, struct IMAGE* target)
{
  int x;
  int y;
  int midX;
  int midY;
  int midMax;
  int halfX;
  int halfY;
  int dX;
  int dY;
  float m11;
  float m12;
  float m21;
  float m22;
  int diffX;
  int diffY;
  int oldX;
  int oldY;
  int pixel;
  float sinval;
  float cosval;
  int w, h;

  w = source->width;
  h = source->height;
  halfX = (w-1)/2;
  halfY = (h-1)/2;
  midX = w/2;
  midY = h/2;
  midMax = max(midX, midY);

  // create 2D rotation matrix
  sinval = sin(radians); // no use of sincos()-function for compatibility, no performace bottleneck anymore anyway
  cosval = cos(radians);
  m11 = cosval;
  m12 = sinval;
  m21 = -sinval;
  m22 = cosval;

  // step through all pixels of the target image,
  // symmetrically in all four quadrants to reduce trigonometric calculations

  for (dY = 0; dY <= midMax; dY++)
    {

      for (dX = 0; dX <= midMax; dX++)
        {

          // matrix multiplication to get rotated pixel pos (as in quadrant I)
          diffX = dX * m11 + dY * m21;
          diffY = dX * m12 + dY * m22;

          // quadrant I
          x = midX + dX;
          y = midY - dY;
          if ((x < w) && (y >= 0))
            {
              oldX = midX + diffX;
              oldY = midY - diffY;
              pixel = getPixel(oldX, oldY, source);
              setPixel(pixel, x, y, target);
            }

          // quadrant II
          x = halfX - dY;
          y = midY - dX;
          if ((x >=0) && (y >= 0))
            {
              oldX = halfX - diffY;
              oldY = midY - diffX;
              pixel = getPixel(oldX, oldY, source);
              setPixel(pixel, x, y, target);
            }

          // quadrant III
          x = halfX - dX;
          y = halfY + dY;
          if ((x >=0) && (y < h))
            {
              oldX = halfX - diffX;
              oldY = halfY + diffY;
              pixel = getPixel(oldX, oldY, source);
              setPixel(pixel, x, y, target);
            }

          // quadrant IV
          x = midX + dY;
          y = halfY + dX;
          if ((x < w) && (y < h))
            {
              oldX = midX + diffY;
              oldY = halfY + diffX;
              pixel = getPixel(oldX, oldY, source);
              setPixel(pixel, x, y, target);
            }
        }
    }
}


/**
 * Converts an image buffer to a qpixel-representation, i.e. enlarge the whole
 * whole image both horizontally and vertically by factor 2 (leading to a
 * factor 4 increase in total).
 * qpixelBuf must have been allocated before with 4-times amount of memory as
 * buf.
 */
void convertToQPixels(struct IMAGE* image, struct IMAGE* qpixelImage)
{
  int x;
  int y;
  int xx;
  int yy;
  int pixel;

  yy = 0;
  for (y = 0; y < image->height; y++)
    {
      xx = 0;
      for (x = 0; x < image->width; x++)
        {
          pixel = getPixel(x, y, image);
          setPixel(pixel, xx, yy, qpixelImage);
          setPixel(pixel, xx+1, yy, qpixelImage);
          setPixel(pixel, xx, yy+1, qpixelImage);
          setPixel(pixel, xx+1, yy+1, qpixelImage);
          xx += 2;
        }
      yy += 2;
    }
}


/**
 * Converts an image buffer back from a qpixel-representation to normal, i.e.
 * shrinks the whole image both horizontally and vertically by factor 2
 * (leading to a factor 4 decrease in total).
 * buf must have been allocated before with 1/4-times amount of memory as
 * qpixelBuf.
 */
void convertFromQPixels(struct IMAGE* qpixelImage, struct IMAGE* image)
{
  int x;
  int y;
  int xx;
  int yy;
  int pixel;
  int a,b,c,d;
  int r, g, bl;

  yy = 0;
  for (y = 0; y < image->height; y++)
    {
      xx = 0;
      for (x = 0; x < image->width; x++)
        {
          a = getPixel(xx, yy, qpixelImage);
          b = getPixel(xx+1, yy, qpixelImage);
          c = getPixel(xx, yy+1, qpixelImage);
          d = getPixel(xx+1, yy+1, qpixelImage);
          r = (red(a) + red(b) + red(c) + red(d)) / 4;
          g = (green(a) + green(b) + green(c) + green(d)) / 4;
          bl = (blue(a) + blue(b) + blue(c) + blue(d)) / 4;
          pixel = pixelValue(r, g, bl);
          setPixel(pixel, x, y, image);
          xx += 2;
        }
      yy += 2;
    }
}


/* --- stretching / resizing / shifting ------------------------------------ */

/**
 * Stretches the image so that the resulting image has a new size.
 *
 * @param w the new width to stretch to
 * @param h the new height to stretch to
 */
void stretch(int w, int h, struct IMAGE* image)
{
  struct IMAGE newimage;
  int x;
  int y;
  int matrixX;
  int matrixY;
  int matrixWidth;
  int matrixHeight;
  int blockWidth;
  int blockHeight;
  int blockWidthRest;
  int blockHeightRest;
  int fillIndexWidth;
  int fillIndexHeight;
  int fill;
  int xx;
  int yy;
  int sum;
  int sumR;
  int sumG;
  int sumB;
  int sumCount;
  int pixel;


  // allocate new buffer's memory
  initImage(&newimage, w, h, image->bitdepth, image->color, WHITE);

  blockWidth = image->width / w; // (0 if enlarging, i.e. w > image->width)
  blockHeight = image->height / h;

  if (w <= image->width)
    {
      blockWidthRest = (image->width) % w;
    }
  else     // modulo-operator doesn't work as expected: (3680 % 7360)==3680 ! (not 7360 as expected)
    {
      // shouldn't always be a % b = b if a < b ?
      blockWidthRest = w;
    }

  if (h <= image->height)
    {
      blockHeightRest = (image->height) % h;
    }
  else
    {
      blockHeightRest = h;
    }

  // for each new pixel, get a matrix of pixels from which the new pixel should be derived
  // (when enlarging, this matrix is always of size 1x1)
  matrixY = 0;
  fillIndexHeight = 0;
  for (y = 0; y < h; y++)
    {
      fillIndexWidth = 0;
      matrixX = 0;
      if ( ( (y * blockHeightRest) / h ) == fillIndexHeight )   // next fill index?
        {
          // (If our optimizer is cool, the above "* blockHeightRest / h" will disappear
          // when images are enlarged, because in that case blockHeightRest = h has been set before,
          // thus we're in a Kripke-branch where blockHeightRest and h are the same variable.
          // No idea if gcc's optimizer does this...) (See again below.)
          fillIndexHeight++;
          fill = 1;
        }
      else
        {
          fill = 0;
        }
      matrixHeight = blockHeight + fill;
      for (x = 0; x < w; x++)
        {
          if ( ( (x * blockWidthRest) / w ) == fillIndexWidth )   // next fill index?
            {
              fillIndexWidth++;
              fill = 1;
            }
          else
            {
              fill = 0;
            }
          matrixWidth = blockWidth + fill;
          // if enlarging, map corrdinates directly
          if (blockWidth == 0)   // enlarging
            {
              matrixX = (x * image->width) / w;
            }
          if (blockHeight == 0)   // enlarging
            {
              matrixY = (y * image->height) / h;
            }

          // calculate average pixel value in source matrix
          if ((matrixWidth == 1) && (matrixHeight == 1))   // optimization: quick version
            {
              pixel = getPixel(matrixX, matrixY, image);
            }
          else
            {
              sumCount = 0;
              if (!image->color)
                {
                  sum = 0;
                  for (yy = 0; yy < matrixHeight; yy++)
                    {
                      for (xx = 0; xx < matrixWidth; xx++)
                        {
                          sum += getPixelGrayscale(matrixX + xx, matrixY + yy, image);
                          sumCount++;
                        }
                    }
                  sum = sum / sumCount;
                  pixel = pixelGrayscaleValue(sum);
                }
              else     // color
                {
                  sumR = 0;
                  sumG = 0;
                  sumB = 0;
                  for (yy = 0; yy < matrixHeight; yy++)
                    {
                      for (xx = 0; xx < matrixWidth; xx++)
                        {
                          pixel = getPixel(matrixX + xx, matrixY + yy, image);
                          sumR += (pixel >> 16) & 0xff;
                          sumG += (pixel >> 8) & 0xff;
                          sumB += pixel & 0xff;
                          //sumR += getPixelComponent(matrixX + xx, matrixY + yy, RED, image);
                          //sumG += getPixelComponent(matrixX + xx, matrixY + yy, GREEN, image);
                          //sumB += getPixelComponent(matrixX + xx, matrixY + yy, BLUE, image);
                          sumCount++;
                        }
                    }
                  pixel = pixelValue( sumR/sumCount, sumG/sumCount, sumB/sumCount );
                }
            }
          setPixel(pixel, x, y, &newimage);

          // pixel may have resulted in a gray value, which will be converted to 1-bit
          // when the file gets saved, if .pbm format requested. black-threshold will apply.

          if (blockWidth > 0)   // shrinking
            {
              matrixX += matrixWidth;
            }
        }
      if (blockHeight > 0)   // shrinking
        {
          matrixY += matrixHeight;
        }
    }
  replaceImage(image, &newimage);
}

/**
 * Resizes the image so that the resulting sheet has a new size and the image
 * content is zoomed to fit best into the sheet, while keeping it's aspect ration.
 *
 * @param w the new width to resize to
 * @param h the new height to resize to
 */
void resize(int w, int h, struct IMAGE* image)
{
  struct IMAGE newimage;
  int ww;
  int hh;
  float wRat;
  float hRat;


  wRat = (float)w / image->width;
  hRat = (float)h / image->height;
  if (wRat < hRat)   // horizontally more shrinking/less enlarging is needed: fill width fully, adjust height
    {
      ww = w;
      hh = image->height * w / image->width;
    }
  else if (hRat < wRat)
    {
      ww = image->width * h / image->height;
      hh = h;
    }
  else     // wRat == hRat
    {
      ww = w;
      hh = h;
    }
  stretch(ww, hh, image);
  initImage(&newimage, w, h, image->bitdepth, image->color, image->background);
  centerImage(image, 0, 0, w, h, &newimage);
  replaceImage(image, &newimage);
}


/**
 * Shifts the image.
 *
 * @param shiftX horizontal shifting
 * @param shiftY vertical shifting
 */
void shift(int shiftX, int shiftY, struct IMAGE* image)
{
  struct IMAGE newimage;
  int x;
  int y;
  int pixel;

  // allocate new buffer's memory
  initImage(&newimage, image->width, image->height, image->bitdepth, image->color, image->background);

  for (y = 0; y < image->height; y++)
    {
      for (x = 0; x < image->width; x++)
        {
          pixel = getPixel(x, y, image);
          setPixel(pixel, x + shiftX, y + shiftY, &newimage);
        }
    }
  replaceImage(image, &newimage);
}


/* --- mask-detection ----------------------------------------------------- */

/**
 * Finds one edge of non-black pixels headig from one starting point towards edge direction.
 *
 * @return number of shift-steps until blank edge found
 */
int detectEdge(int startX, int startY, int shiftX, int shiftY, int maskScanSize, int maskScanDepth, float maskScanThreshold, struct IMAGE* image)
{
  // either shiftX or shiftY is 0, the other value is -i|+i
  int left;
  int top;
  int right;
  int bottom;
  int half;
  int halfDepth;
  int blackness;
  int total;
  int count;

  half = maskScanSize / 2;
  total = 0;
  count = 0;
  if (shiftY==0)   // vertical border is to be detected, horizontal shifting of scan-bar
    {
      if (maskScanDepth == -1)
        {
          maskScanDepth = image->height;
        }
      halfDepth = maskScanDepth / 2;
      left = startX - half;
      top = startY - halfDepth;
      right = startX + half;
      bottom = startY + halfDepth;
    }
  else     // horizontal border is to be detected, vertical shifting of scan-bar
    {
      if (maskScanDepth == -1)
        {
          maskScanDepth = image->width;
        }
      halfDepth = maskScanDepth / 2;
      left = startX - halfDepth;
      top = startY - half;
      right = startX + halfDepth;
      bottom = startY + half;
    }

  while (TRUE)   // !
    {
      blackness = 255 - brightnessRect(left, top, right, bottom, image);
      total += blackness;
      count++;
      // is blackness below threshold*average?
      if ((blackness < ((maskScanThreshold*total)/count))||(blackness==0))   // this will surely become true when pos reaches the outside of the actual image area and blacknessRect() will deliver 0 because all pixels outside are considered white
        {
          return count; // ! return here, return absolute value of shifting difference
        }
      left += shiftX;
      right += shiftX;
      top += shiftY;
      bottom += shiftY;
    }
}


/**
 * Detects a mask of white borders around a starting point.
 * The result is returned via call-by-reference parameters left, top, right, bottom.
 *
 * @return the detected mask in left, top, right, bottom; or -1, -1, -1, -1 if no mask could be detected
 */
BOOLEAN detectMask(int startX, int startY, int maskScanDirections, int maskScanSize[DIRECTIONS_COUNT], int maskScanDepth[DIRECTIONS_COUNT], int maskScanStep[DIRECTIONS_COUNT], float maskScanThreshold[DIRECTIONS_COUNT], int maskScanMinimum[DIMENSIONS_COUNT], int maskScanMaximum[DIMENSIONS_COUNT], int* left, int* top, int* right, int* bottom, struct IMAGE* image)
{
  int width;
  int height;
  int half[DIRECTIONS_COUNT];
  BOOLEAN success;

  half[HORIZONTAL] = maskScanSize[HORIZONTAL] / 2;
  half[VERTICAL] = maskScanSize[VERTICAL] / 2;
  if ((maskScanDirections & 1<<HORIZONTAL) != 0)
    {
      *left = startX - maskScanStep[HORIZONTAL] * detectEdge(startX, startY, -maskScanStep[HORIZONTAL], 0, maskScanSize[HORIZONTAL], maskScanDepth[HORIZONTAL], maskScanThreshold[HORIZONTAL], image) - half[HORIZONTAL];
      *right = startX + maskScanStep[HORIZONTAL] * detectEdge(startX, startY, maskScanStep[HORIZONTAL], 0, maskScanSize[HORIZONTAL], maskScanDepth[HORIZONTAL], maskScanThreshold[HORIZONTAL], image) + half[HORIZONTAL];
    }
  else     // full range of sheet
    {
      *left = 0;
      *right = image->width - 1;
    }
  if ((maskScanDirections & 1<<VERTICAL) != 0)
    {
      *top = startY - maskScanStep[VERTICAL] * detectEdge(startX, startY, 0, -maskScanStep[VERTICAL], maskScanSize[VERTICAL], maskScanDepth[VERTICAL], maskScanThreshold[VERTICAL], image) - half[VERTICAL];
      *bottom = startY + maskScanStep[VERTICAL] * detectEdge(startX, startY, 0, maskScanStep[VERTICAL], maskScanSize[VERTICAL], maskScanDepth[VERTICAL], maskScanThreshold[VERTICAL], image) + half[VERTICAL];
    }
  else     // full range of sheet
    {
      *top = 0;
      *bottom = image->height - 1;
    }

  // if below minimum or above maximum, set to maximum
  width = *right - *left;
  height = *bottom - *top;
  success = TRUE;
  if ( ((maskScanMinimum[WIDTH] != -1) && (width < maskScanMinimum[WIDTH])) || ((maskScanMaximum[WIDTH] != -1) && (width > maskScanMaximum[WIDTH])) )
    {
      width = maskScanMaximum[WIDTH] / 2;
      *left = startX - width;
      *right = startX + width;
      success = FALSE;;
    }
  if ( ((maskScanMinimum[HEIGHT] != -1) && (height < maskScanMinimum[HEIGHT])) || ((maskScanMaximum[HEIGHT] != -1) && (height > maskScanMaximum[HEIGHT])) )
    {
      height = maskScanMaximum[HEIGHT] / 2;
      *top = startY - height;
      *bottom = startY + height;
      success = FALSE;
    }
  return success;
}


/**
 * Detects masks around the points specified in point[].
 *
 * @param mask point to array into which detected masks will be stored
 * @return number of masks stored in mask[][]
 */
int detectMasks(int mask[MAX_MASKS][EDGES_COUNT], BOOLEAN maskValid[MAX_MASKS], int point[MAX_POINTS][COORDINATES_COUNT], int pointCount, int maskScanDirections, int maskScanSize[DIRECTIONS_COUNT], int maskScanDepth[DIRECTIONS_COUNT], int maskScanStep[DIRECTIONS_COUNT], float maskScanThreshold[DIRECTIONS_COUNT], int maskScanMinimum[DIMENSIONS_COUNT], int maskScanMaximum[DIMENSIONS_COUNT],  struct IMAGE* image)
{
  int left;
  int top;
  int right;
  int bottom;
  int i;
  int maskCount;

  maskCount = 0;
  if (maskScanDirections != 0)
    {
      for (i = 0; i < pointCount; i++)
        {
          maskValid[i] = detectMask(point[i][X], point[i][Y], maskScanDirections, maskScanSize, maskScanDepth, maskScanStep, maskScanThreshold, maskScanMinimum, maskScanMaximum, &left, &top, &right, &bottom, image);
          if (!(left==-1 || top==-1 || right==-1 || bottom==-1))
            {
              mask[maskCount][LEFT] = left;
              mask[maskCount][TOP] = top;
              mask[maskCount][RIGHT] = right;
              mask[maskCount][BOTTOM] = bottom;
              maskCount++;
            }
          //if (maskValid[i] == FALSE) { // (mask had been auto-set to full page size)
          //    if (verbose>=VERBOSE_NORMAL) {
          //        printf("auto-masking (%d,%d): NO MASK DETECTED\n", point[i][X], point[i][Y]);
          //    }
          //}
        }
    }
  return maskCount;
}


/**
 * Permanently applies image masks. Each pixel which is not covered by at least
 * one mask is set to maskColor.
 */
void applyMasks(int mask[MAX_MASKS][EDGES_COUNT], int maskCount, int maskColor, struct IMAGE* image)
{
  int x;
  int y;
  int i;
  int left, top, right, bottom;
  BOOLEAN m;

  if (maskCount<=0)
    {
      return;
    }
  for (y=0; y < image->height; y++)
    {
      for (x=0; x < image->width; x++)
        {
          // in any mask?
          m = FALSE;
          for (i=0; ((m==FALSE) && (i<maskCount)); i++)
            {
              left = mask[i][LEFT];
              top = mask[i][TOP];
              right = mask[i][RIGHT];
              bottom = mask[i][BOTTOM];
              if (y>=top && y<=bottom && x>=left && x<=right)
                {
                  m = TRUE;
                }
            }
          if (m == FALSE)
            {
              setPixel(maskColor, x, y, image); // delete: set to white
            }
        }
    }
}


/* --- wiping ------------------------------------------------------------- */

/**
 * Permanently wipes out areas of an images. Each pixel covered by a wipe-area
 * is set to wipeColor.
 */
void applyWipes(int area[MAX_MASKS][EDGES_COUNT], int areaCount, int wipeColor, struct IMAGE* image)
{
  int x;
  int y;
  int i;
  int count;

  for (i = 0; i < areaCount; i++)
    {
      count = 0;
      for (y = area[i][TOP]; y <= area[i][BOTTOM]; y++)
        {
          for (x = area[i][LEFT]; x <= area[i][RIGHT]; x++)
            {
              if ( setPixel(wipeColor, x, y, image) )
                {
                  count++;
                }
            }
        }
    }
}


/* --- mirroring ---------------------------------------------------------- */

/**
 * Mirrors an image either horizontally, vertically, or both.
 */
void mirror(int directions, struct IMAGE* image)
{
  int x;
  int y;
  int xx;
  int yy;
  int pixel1;
  int pixel2;
  BOOLEAN horizontal;
  BOOLEAN vertical;
  int untilX;
  int untilY;

  horizontal = ((directions & 1<<HORIZONTAL) != 0) ? TRUE : FALSE;
  vertical = ((directions & 1<<VERTICAL) != 0) ? TRUE : FALSE;
  untilX = ((horizontal==TRUE)&&(vertical==FALSE)) ? ((image->width - 1) >> 1) : (image->width - 1);  // w>>1 == (int)(w-0.5)/2
  untilY = (vertical==TRUE) ? ((image->height - 1) >> 1) : image->height - 1;
  for (y = 0; y <= untilY; y++)
    {
      yy = (vertical==TRUE) ? (image->height - y - 1) : y;
      if ((vertical==TRUE) && (horizontal==TRUE) && (y == yy))   // last middle line in odd-lined image mirrored both h and v
        {
          untilX = ((image->width - 1) >> 1);
        }
      for (x = 0; x <= untilX; x++)
        {
          xx = (horizontal==TRUE) ? (image->width - x - 1) : x;
          pixel1 = getPixel(x, y, image);
          pixel2 = getPixel(xx, yy, image);
          setPixel(pixel2, x, y, image);
          setPixel(pixel1, xx, yy, image);
        }
    }
}


/* --- flip-rotating ------------------------------------------------------ */

/**
 * Rotates an image clockwise or anti-clockwise in 90-degrees.
 *
 * @param direction either -1 (rotate anti-clockwise) or 1 (rotate clockwise)
 */
void flipRotate(int direction, struct IMAGE* image)
{
  struct IMAGE newimage;
  int x;
  int y;
  int xx;
  int yy;
  int pixel;

  initImage(&newimage, image->height, image->width, image->bitdepth, image->color, WHITE); // exchanged width and height
  for (y = 0; y < image->height; y++)
    {
      xx = ((direction > 0) ? image->height - 1 : 0) - y * direction;
      for (x = 0; x < image->width; x++)
        {
          yy = ((direction < 0) ? image->width - 1 : 0) + x*direction;
          pixel = getPixel(x, y, image);
          setPixel(pixel, xx, yy, &newimage);
        }
    }
  replaceImage(image, &newimage);
}


/* --- blackfilter -------------------------------------------------------- */

/**
 * Filters out solidly black areas scanning to one direction.
 *
 * @param stepX is 0 if stepY!=0
 * @param stepY is 0 if stepX!=0
 * @see blackfilter()
 */
void blackfilterScan(int stepX, int stepY, int size, int dep, float threshold, int exclude[MAX_MASKS][EDGES_COUNT], int excludeCount, int intensity, float blackThreshold, struct IMAGE* image)
{
  int left;
  int top;
  int right;
  int bottom;
  int blackness;
  int thresholdBlack;
  int x;
  int y;
  int shiftX;
  int shiftY;
  int l, t, r, b;
  int total;
  int diffX;
  int diffY;
  int mask[EDGES_COUNT];
  BOOLEAN alreadyExcludedMessage;

  thresholdBlack = (int)(WHITE * (1.0-blackThreshold));
  total = size * dep;
  if (stepX != 0)   // horizontal scanning
    {
      left = 0;
      top = 0;
      right = size -1;
      bottom = dep - 1;
      shiftX = 0;
      shiftY = dep;
    }
  else     // vertical scanning
    {
      left = 0;
      top = 0;
      right = dep -1;
      bottom = size - 1;
      shiftX = dep;
      shiftY = 0;
    }
  while ((left < image->width) && (top < image->height))   // individual scanning "stripes" over the whole sheet
    {
      l = left;
      t = top;
      r = right;
      b = bottom;
      // make sure last stripe does not reach outside sheet, shift back inside (next +=shift will exit while-loop)
      if (r >= image->width || b >= image->height)
        {
          diffX = r-image->width+1;
          diffY = b-image->height+1;
          l -= diffX;
          t -= diffY;
          r -= diffX;
          b -= diffY;
        }
      alreadyExcludedMessage = FALSE;
      while ((l < image->width) && (t < image->height))   // single scanning "stripe"
        {
          blackness = 255 - darknessInverseRect(l, t, r, b, image);
          if (blackness >= 255*threshold)   // found a solidly black area
            {
              mask[LEFT] = l;
              mask[TOP] = t;
              mask[RIGHT] = r;
              mask[BOTTOM] = b;
              if (! masksOverlapAny(mask, exclude, excludeCount) )
                {
                  // start flood-fill in this area (on each pixel to make sure we get everything, in most cases first flood-fill from first pixel will delete all other black pixels in the area already)
                  for (y = t; y <= b; y++)
                    {
                      for (x = l; x <= r; x++)
                        {
                          floodFill(x, y, pixelValue(WHITE, WHITE, WHITE), 0, thresholdBlack, intensity, image);
                        }
                    }
                }
            }
          l += stepX;
          t += stepY;
          r += stepX;
          b += stepY;
        }
      left += shiftX;
      top += shiftY;
      right += shiftX;
      bottom += shiftY;
    }
}


/**
 * Filters out solidly black areas, as appearing on bad photocopies.
 * A virtual bar of width 'size' and height 'depth' is horizontally moved
 * above the middle of the sheet (or the full sheet, if depth ==-1).
 */
void blackfilter(int blackfilterScanDirections, int blackfilterScanSize[DIRECTIONS_COUNT], int blackfilterScanDepth[DIRECTIONS_COUNT], int blackfilterScanStep[DIRECTIONS_COUNT], float blackfilterScanThreshold, int blackfilterExclude[MAX_MASKS][EDGES_COUNT], int blackfilterExcludeCount, int blackfilterIntensity, float blackThreshold, struct IMAGE* image)
{
  if ((blackfilterScanDirections & 1<<HORIZONTAL) != 0)   // left-to-right scan
    {
      blackfilterScan(blackfilterScanStep[HORIZONTAL], 0, blackfilterScanSize[HORIZONTAL], blackfilterScanDepth[HORIZONTAL], blackfilterScanThreshold, blackfilterExclude, blackfilterExcludeCount, blackfilterIntensity, blackThreshold, image);
    }
  if ((blackfilterScanDirections & 1<<VERTICAL) != 0)   // top-to-bottom scan
    {
      blackfilterScan(0, blackfilterScanStep[VERTICAL], blackfilterScanSize[VERTICAL], blackfilterScanDepth[VERTICAL], blackfilterScanThreshold, blackfilterExclude, blackfilterExcludeCount, blackfilterIntensity, blackThreshold, image);
    }
}


/* --- noisefilter -------------------------------------------------------- */

/**
 * Applies a simple noise filter to the image.
 *
 * @param intensity maximum cluster size to delete
 */
int noisefilter(int intensity, float whiteThreshold, struct IMAGE* image)
{
  int x;
  int y;
  int whiteMin;
  int count;
  int pixel;
  int neighbors;

  whiteMin = (int)(WHITE * whiteThreshold);
  count = 0;
  for (y = 0; y < image->height; y++)
    {
      for (x = 0; x < image->width; x++)
        {
          pixel = getPixelDarknessInverse(x, y, image);
          if (pixel < whiteMin)   // one dark pixel found
            {
              neighbors = countPixelNeighbors(x, y, intensity, whiteMin, image); // get number of non-light pixels in neighborhood
              if (neighbors <= intensity)   // ...not more than 'intensity'?
                {
                  clearPixelNeighbors(x, y, whiteMin, image); // delete area
                  count++;
                }
            }
        }
    }
  return count;
}


/* --- blurfilter --------------------------------------------------------- */

/**
 * Removes noise using a kind of blurfilter, as alternative to the noise
 * filter. This algoithm counts pixels while 'shaking' the area to detect,
 * and clears the area if the amount of white pixels exceeds whiteTreshold.
 */
int blurfilter(int blurfilterScanSize[DIRECTIONS_COUNT], int blurfilterScanStep[DIRECTIONS_COUNT], float blurfilterIntensity, float whiteThreshold, struct IMAGE* image)
{
  int whiteMin;
  int left;
  int top;
  int right;
  int bottom;
  int count;
  int max;
  int total;
  int result;

  result = 0;
  whiteMin = (int)(WHITE * whiteThreshold);
  left = 0;
  top = 0;
  right = blurfilterScanSize[HORIZONTAL] - 1;
  bottom = blurfilterScanSize[VERTICAL] - 1;
  total = blurfilterScanSize[HORIZONTAL] * blurfilterScanSize[VERTICAL];

  while (TRUE)   // !
    {
      max = 0;
      count = countPixelsRect(left, top, right, bottom, 0, whiteMin, FALSE, image);
      if (count > max)
        {
          max = count;
        }
      count = countPixelsRect(left-blurfilterScanStep[HORIZONTAL], top-blurfilterScanStep[VERTICAL], right-blurfilterScanStep[HORIZONTAL], bottom-blurfilterScanStep[VERTICAL], 0, whiteMin, FALSE, image);
      if (count > max)
        {
          max = count;
        }
      count = countPixelsRect(left+blurfilterScanStep[HORIZONTAL], top-blurfilterScanStep[VERTICAL], right+blurfilterScanStep[HORIZONTAL], bottom-blurfilterScanStep[VERTICAL], 0, whiteMin, FALSE, image);
      if (count > max)
        {
          max = count;
        }
      count = countPixelsRect(left-blurfilterScanStep[HORIZONTAL], top+blurfilterScanStep[VERTICAL], right-blurfilterScanStep[HORIZONTAL], bottom+blurfilterScanStep[VERTICAL], 0, whiteMin, FALSE, image);
      if (count > max)
        {
          max = count;
        }
      count = countPixelsRect(left+blurfilterScanStep[HORIZONTAL], top+blurfilterScanStep[VERTICAL], right+blurfilterScanStep[HORIZONTAL], bottom+blurfilterScanStep[VERTICAL], 0, whiteMin, FALSE, image);
      if (count > max)
        {
          max = count;
        }
      if ((((float)max)/total) <= blurfilterIntensity)
        {
          result += countPixelsRect(left, top, right, bottom, 0, whiteMin, TRUE, image); // also clear
        }
      if (right < image->width)   // not yet at end of row
        {
          left += blurfilterScanStep[HORIZONTAL];
          right += blurfilterScanStep[HORIZONTAL];
        }
      else     // end of row
        {
          if (bottom >= image->height)   // has been last row
            {
              return result; // exit here
            }
          // next row:
          left = 0;
          right = blurfilterScanSize[HORIZONTAL] - 1;
          top += blurfilterScanStep[VERTICAL];
          bottom += blurfilterScanStep[VERTICAL];
        }
    }
}


/* --- grayfilter --------------------------------------------------------- */

/**
 * Clears areas which do not contain any black pixels, but some "gray shade" only.
 * Two conditions have to apply before an area gets deleted: first, not a single black pixel may be contained,
 * second, a minimum threshold of blackness must not be exceeded.
 */
int grayfilter(int grayfilterScanSize[DIRECTIONS_COUNT], int grayfilterScanStep[DIRECTIONS_COUNT], float grayfilterThreshold, float blackThreshold, struct IMAGE* image)
{
  int blackMax;
  int left;
  int top;
  int right;
  int bottom;
  int count;
  int lightness;
  int thresholdAbs;
  int total;
  int result;

  result = 0;
  blackMax = (int)(WHITE * (1.0-blackThreshold));
  thresholdAbs = (int)(WHITE * grayfilterThreshold);
  left = 0;
  top = 0;
  right = grayfilterScanSize[HORIZONTAL] - 1;
  bottom = grayfilterScanSize[VERTICAL] - 1;
  total = grayfilterScanSize[HORIZONTAL] * grayfilterScanSize[VERTICAL];

  while (TRUE)   // !
    {
      count = countPixelsRect(left, top, right, bottom, 0, blackMax, FALSE, image);
      if (count == 0)
        {
          lightness = lightnessRect(left, top, right, bottom, image);
          if ((WHITE - lightness) < thresholdAbs)   // (lower threshold->more deletion)
            {
              result += clearRect(left, top, right, bottom, image, WHITE);
            }
        }
      if (left < image->width)   // not yet at end of row
        {
          left += grayfilterScanStep[HORIZONTAL];
          right += grayfilterScanStep[HORIZONTAL];
        }
      else     // end of row
        {
          if (bottom >= image->height)   // has been last row
            {
              return result; // exit here
            }
          // next row:
          left = 0;
          right = grayfilterScanSize[HORIZONTAL] - 1;
          top += grayfilterScanStep[VERTICAL];
          bottom += grayfilterScanStep[VERTICAL];
        }
    }
}


/* --- border-detection --------------------------------------------------- */

/**
 * Moves a rectangular area of pixels to be centered above the centerX, centerY coordinates.
 */
void centerMask(int centerX, int centerY, int left, int top, int right, int bottom, struct IMAGE* image, int &dx, int &dy)
{
  struct IMAGE newimage;
  int width;
  int height;
  int targetX;
  int targetY;

  width = right - left + 1;
  height = bottom - top + 1;
  targetX = centerX - width/2;
  targetY = centerY - height/2;
  //  printf("centerMask %d %d\n",targetX-left,targetY-top);
  dx = targetX-left;
  dy = targetY-top;
  if ((targetX >= 0) && (targetY >= 0) && ((targetX+width) <= image->width) && ((targetY+height) <= image->height))
    {
      initImage(&newimage, width, height, image->bitdepth, image->color, image->background);
      copyImageArea(left, top, width, height, image, 0, 0, &newimage);
      clearRect(left, top, right, bottom, image, image->background);
      copyImageArea(0, 0, width, height, &newimage, targetX, targetY, image);
      freeImage(&newimage);
    }
}


/**
 * Moves a rectangular area of pixels to be centered inside a specified area coordinates.
 */
void alignMask(int mask[EDGES_COUNT], int outside[EDGES_COUNT], int direction, int margin[DIRECTIONS_COUNT], struct IMAGE* image, int &dx, int &dy)
{
  struct IMAGE newimage;
  int width;
  int height;
  int targetX;
  int targetY;

  width = mask[RIGHT] - mask[LEFT] + 1;
  height = mask[BOTTOM] - mask[TOP] + 1;
  if (direction & 1<<LEFT)
    {
      targetX = outside[LEFT] + margin[HORIZONTAL];
    }
  else if (direction & 1<<RIGHT)
    {
      targetX = outside[RIGHT] - width - margin[HORIZONTAL];
    }
  else
    {
      targetX = (outside[LEFT] + outside[RIGHT] - width) / 2;
    }
  if (direction & 1<<TOP)
    {
      targetY = outside[TOP] + margin[VERTICAL];
    }
  else if (direction & 1<<BOTTOM)
    {
      targetY = outside[BOTTOM] - height - margin[VERTICAL];
    }
  else
    {
      targetY = (outside[TOP] + outside[BOTTOM] - height) / 2;
    }
  //  printf("alignMask %d %d\n",targetX-mask[LEFT], targetY-mask[TOP]);
  dx = targetX-mask[LEFT];
  dy =  targetY-mask[TOP];
  initImage(&newimage, width, height, image->bitdepth, image->color, image->background);
  copyImageArea(mask[LEFT], mask[TOP], mask[RIGHT], mask[BOTTOM], image, 0, 0, &newimage);
  clearRect(mask[LEFT], mask[TOP], mask[RIGHT], mask[BOTTOM], image, image->background);
  copyImageArea(0, 0, width, height, &newimage, targetX, targetY, image);
  freeImage(&newimage);
}

/**
 * Find the size of one border edge.
 *
 * @param x1..y2 area inside of which border is to be detected
 */
int detectBorderEdge(int outsideMask[EDGES_COUNT], int stepX, int stepY, int size, int threshold, int maxBlack, struct IMAGE* image)
{
  int left;
  int top;
  int right;
  int bottom;
  int max;
  int cnt;
  int result;

  if (stepY == 0)   // horizontal detection
    {
      if (stepX > 0)
        {
          left = outsideMask[LEFT];
          top = outsideMask[TOP];
          right = outsideMask[LEFT] + size;
          bottom = outsideMask[BOTTOM];
        }
      else
        {
          left = outsideMask[RIGHT] - size;
          top = outsideMask[TOP];
          right = outsideMask[RIGHT];
          bottom = outsideMask[BOTTOM];
        }
      max = (outsideMask[RIGHT] - outsideMask[LEFT]);
    }
  else     // vertical detection
    {
      if (stepY > 0)
        {
          left = outsideMask[LEFT];
          top = outsideMask[TOP];
          right = outsideMask[RIGHT];
          bottom = outsideMask[TOP] + size;
        }
      else
        {
          left = outsideMask[LEFT];
          top = outsideMask[BOTTOM] - size;
          right = outsideMask[RIGHT];
          bottom = outsideMask[BOTTOM];
        }
      max = (outsideMask[BOTTOM] - outsideMask[TOP]);
    }
  result = 0;
  while (result < max)
    {
      cnt = countPixelsRect(left, top, right, bottom, 0, maxBlack, FALSE, image);
      if (cnt >= threshold)
        {
          return result; // border has been found: regular exit here
        }
      left += stepX;
      top += stepY;
      right += stepX;
      bottom += stepY;
      result += abs(stepX+stepY); // (either stepX or stepY is 0)
    }
  return 0; // no border found between 0..max
}


/**
 * Detects a border of completely non-black pixels around the area outsideBorder[LEFT],outsideBorder[TOP]-outsideBorder[RIGHT],outsideBorder[BOTTOM].
 */
void detectBorder(int border[EDGES_COUNT], int borderScanDirections, int borderScanSize[DIRECTIONS_COUNT], int borderScanStep[DIRECTIONS_COUNT], int borderScanThreshold[DIRECTIONS_COUNT], float blackThreshold, int outsideMask[EDGES_COUNT], struct IMAGE* image)
{
  int blackThresholdAbs;

  border[LEFT] = outsideMask[LEFT];
  border[TOP] = outsideMask[TOP];
  border[RIGHT] = image->width - outsideMask[RIGHT];
  border[BOTTOM] = image->height - outsideMask[BOTTOM];

  blackThresholdAbs = (int)(WHITE * (1.0 - blackThreshold));
  if (borderScanDirections & 1<<HORIZONTAL)
    {
      border[LEFT] += detectBorderEdge(outsideMask, borderScanStep[HORIZONTAL], 0, borderScanSize[HORIZONTAL], borderScanThreshold[HORIZONTAL], blackThresholdAbs, image);
      border[RIGHT] += detectBorderEdge(outsideMask, -borderScanStep[HORIZONTAL], 0, borderScanSize[HORIZONTAL], borderScanThreshold[HORIZONTAL], blackThresholdAbs, image);
    }
  if (borderScanDirections & 1<<VERTICAL)
    {
      border[TOP] += detectBorderEdge(outsideMask, 0, borderScanStep[VERTICAL], borderScanSize[VERTICAL], borderScanThreshold[VERTICAL], blackThresholdAbs, image);
      border[BOTTOM] += detectBorderEdge(outsideMask, 0, -borderScanStep[VERTICAL], borderScanSize[VERTICAL], borderScanThreshold[VERTICAL], blackThresholdAbs, image);
    }
}


/**
 * Converts a border-tuple to a mask-tuple.
 */
void borderToMask(int border[EDGES_COUNT], int mask[EDGES_COUNT], struct IMAGE* image)
{
  mask[LEFT] = border[LEFT];
  mask[TOP] = border[TOP];
  mask[RIGHT] = image->width - border[RIGHT] - 1;
  mask[BOTTOM] = image->height - border[BOTTOM] - 1;
}


/**
 * Applies a border to the whole image. All pixels in the border range at the
 * edges of the sheet will be cleared.
 */
void applyBorder(int border[EDGES_COUNT], int borderColor, struct IMAGE* image)
{
  int mask[EDGES_COUNT];

  if (border[LEFT]!=0 || border[TOP]!=0 || border[RIGHT]!=0 || border[BOTTOM]!=0)
    {
      borderToMask(border, mask, image);
      applyMasks(&mask, 1, borderColor, image);
    }
}

inline void fromImageToStruct(const Magick::Image &source, struct IMAGE* image, int* type)
{
  *type = PGM;

  image->bitdepth = 8;
  image->color = FALSE;
  image->width = source.columns();
  image->height = source.rows();

  int bytesPerLine = image->width;
  int inputSize = bytesPerLine * image->height;

  image->buffer = (unsigned char*) malloc(inputSize);
  Magick::ColorGray c;

  for (int j = 0; j < image->height; j++)
    for (int i = 0; i < image->width; i++)
      {
        c = source.pixelColor(i, j);
        image->buffer[j * bytesPerLine + i] = (unsigned char) (255 * c.shade());
      }

  image->bufferGrayscale = image->buffer;
  image->bufferLightness = image->buffer;
  image->bufferDarknessInverse = image->buffer;
}

inline void fromStructToImage(Magick::Image &target, struct IMAGE* image)
{
  int bytesPerLine = image->width;
  Magick::ColorGray c;

  target.modifyImage();
  target.erase();

  for (int j = 0; j < image->height; j++)
    for (int i = 0; i < image->width; i++)
      {
        //printf("%d ", image->buffer[j * bytesPerLine + i]);
        c.shade((1. * image->buffer[j * bytesPerLine + i]) / 255);
        target.pixelColor(i, j, c);
      }

  //target.write("debug.png");
}

/****************************************************************************
 * MAIN()                                                                   *
 ****************************************************************************/

/**
 * The main program.
 */
int unpaper(Magick::Image &picture, double &radians, int &unpaper_dx, int &unpaper_dy)
{


  // --- parameter variables ---
  int layout;
  int startSheet;
  int endSheet;
  int startInput;
  int startOutput;
  int inputCount;
  int outputCount;
  const char* inputFileSequence[MAX_FILES];
  int inputFileSequenceCount;
  const char* outputFileSequence[MAX_FILES];
  int outputFileSequenceCount;
  int sheetSize[DIMENSIONS_COUNT];
  int sheetBackground;
  int preRotate;
  int postRotate;
  int preMirror;
  int postMirror;
  int preShift[DIRECTIONS_COUNT];
  int postShift[DIRECTIONS_COUNT];
  int size[DIRECTIONS_COUNT];
  int postSize[DIRECTIONS_COUNT];
  int stretchSize[DIRECTIONS_COUNT];
  int postStretchSize[DIRECTIONS_COUNT];
  float zoomFactor;
  float postZoomFactor;
  int pointCount;
  int point[MAX_POINTS][COORDINATES_COUNT];
  int maskCount;
  int mask[MAX_MASKS][EDGES_COUNT];
  int wipeCount;
  int wipe[MAX_MASKS][EDGES_COUNT];
  int middleWipe[2];
  int preWipeCount;
  int preWipe[MAX_MASKS][EDGES_COUNT];
  int postWipeCount;
  int postWipe[MAX_MASKS][EDGES_COUNT];
  int preBorder[EDGES_COUNT];
  int postBorder[EDGES_COUNT];
  int border[EDGES_COUNT];
  BOOLEAN maskValid[MAX_MASKS];
  int preMaskCount;
  int preMask[MAX_MASKS][EDGES_COUNT];
  int blackfilterScanDirections;
  int blackfilterScanSize[DIRECTIONS_COUNT];
  int blackfilterScanDepth[DIRECTIONS_COUNT];
  int blackfilterScanStep[DIRECTIONS_COUNT];
  float blackfilterScanThreshold;
  int blackfilterExcludeCount;
  int blackfilterExclude[MAX_MASKS][EDGES_COUNT];
  int blackfilterIntensity;
  int noisefilterIntensity;
  int blurfilterScanSize[DIRECTIONS_COUNT];
  int blurfilterScanStep[DIRECTIONS_COUNT];
  float blurfilterIntensity;
  int grayfilterScanSize[DIRECTIONS_COUNT];
  int grayfilterScanStep[DIRECTIONS_COUNT];
  float grayfilterThreshold;
  int maskScanDirections;
  int maskScanSize[DIRECTIONS_COUNT];
  int maskScanDepth[DIRECTIONS_COUNT];
  int maskScanStep[DIRECTIONS_COUNT];
  float maskScanThreshold[DIRECTIONS_COUNT];
  int maskScanMinimum[DIMENSIONS_COUNT];
  int maskScanMaximum[DIMENSIONS_COUNT];
  int maskColor;
  int deskewScanEdges;
  int deskewScanSize;
  float deskewScanDepth;
  float deskewScanRange;
  float deskewScanStep;
  float deskewScanDeviation;
  int borderScanDirections;
  int borderScanSize[DIRECTIONS_COUNT];
  int borderScanStep[DIRECTIONS_COUNT];
  int borderScanThreshold[DIRECTIONS_COUNT];
  int borderAlign;
  int borderAlignMargin[DIRECTIONS_COUNT];
  int outsideBorderscanMask[MAX_PAGES][EDGES_COUNT]; // set by --layout
  int outsideBorderscanMaskCount;
  float whiteThreshold;
  float blackThreshold;
  BOOLEAN writeoutput;
  BOOLEAN qpixels;
  BOOLEAN multisheets;
  char* outputTypeName;
  int noBlackfilterMultiIndex[MAX_MULTI_INDEX];
  int noBlackfilterMultiIndexCount;
  int noNoisefilterMultiIndex[MAX_MULTI_INDEX];
  int noNoisefilterMultiIndexCount;
  int noBlurfilterMultiIndex[MAX_MULTI_INDEX];
  int noBlurfilterMultiIndexCount;
  int noGrayfilterMultiIndex[MAX_MULTI_INDEX];
  int noGrayfilterMultiIndexCount;
  int noMaskScanMultiIndex[MAX_MULTI_INDEX];
  int noMaskScanMultiIndexCount;
  int noMaskCenterMultiIndex[MAX_MULTI_INDEX];
  int noMaskCenterMultiIndexCount;
  int noDeskewMultiIndex[MAX_MULTI_INDEX];
  int noDeskewMultiIndexCount;
  int noWipeMultiIndex[MAX_MULTI_INDEX];
  int noWipeMultiIndexCount;
  int noBorderMultiIndex[MAX_MULTI_INDEX];
  int noBorderMultiIndexCount;
  int noBorderScanMultiIndex[MAX_MULTI_INDEX];
  int noBorderScanMultiIndexCount;
  int noBorderAlignMultiIndex[MAX_MULTI_INDEX];
  int noBorderAlignMultiIndexCount;
  int sheetMultiIndex[MAX_MULTI_INDEX];
  int sheetMultiIndexCount;
  int excludeMultiIndex[MAX_MULTI_INDEX];
  int excludeMultiIndexCount;
  int ignoreMultiIndex[MAX_MULTI_INDEX];
  int ignoreMultiIndexCount;
  int autoborder[MAX_MASKS][EDGES_COUNT];
  int autoborderMask[MAX_MASKS][EDGES_COUNT];
  int insertBlankCount;
  int replaceBlankCount;
  BOOLEAN overwrite;
  BOOLEAN showTime;
  int dpi;

  // --- local variables ---
  int w;
  int h;
  int i;
  int j = 0;
  int previousWidth;
  int previousHeight;
  int previousBitdepth;
  BOOLEAN previousColor;
  int inputFileSequencePos; // index 'looping' through input-file-seq (without additionally inserted blank images)
  int outputFileSequencePos; // index 'looping' through output-file-seq
  int inputFileSequencePosTotal; // index 'looping' through input-file-seq (with additional blank images)
  struct IMAGE sheet;
  struct IMAGE sheetBackup;
  struct IMAGE originalSheet;
  struct IMAGE qpixelSheet;
  struct IMAGE page;
  const char* layoutStr;
  char* inputTypeName;
  char* inputTypeNames[MAX_PAGES];
  int inputType;
  int filterResult;
  double rotation;
  int q;
  struct IMAGE rect;
  struct IMAGE rectTarget;
  int outputType;
  int outputDepth;
  int bd;
  BOOLEAN col;
  BOOLEAN success;
  BOOLEAN anyWildcards;
  BOOLEAN allInputFilesMissing;
  int nr = 0;
  int inputNr;
  int outputNr;
  BOOLEAN first;
  clock_t startTime;
  clock_t endTime;
  clock_t time;
  unsigned long int totalTime;
  int totalCount;
  int blankCount;
  int exitCode;

  sheet.buffer = NULL;
  page.buffer = NULL;
  exitCode = 0; // error code to return
  bd = 1; // default bitdepth if not resolvable (i.e. usually empty input, so bd=1 is good choice)
  col = FALSE; // default no color if not resolvable

  // explicitly un-initialize variables that are sometimes not used to avoid compiler warnings
  qpixelSheet.buffer = NULL; // used optionally, deactivated by --no-qpixels
  startTime = 0;             // used optionally in debug mode -vv or with --time
  endTime = 0;               // used optionally in debug mode -vv or with --time
  inputNr = -1;              // will be initialized in first run of main-loop
  outputNr = -1;             // will be initialized in first run of main-loop


  // -----------------------------------------------------------------------
  // --- process all sheets                                              ---
  // -----------------------------------------------------------------------

  // count from start sheet to end sheet
  startSheet = 1; // defaults, may be changed in first run of for-loop
  endSheet = -1;
  startInput = -1;
  startOutput = -1;
  totalTime = 0;
  totalCount = 0;
  inputFileSequencePos = 0;
  outputFileSequencePos = 0;
  inputFileSequencePosTotal = 0;
  previousWidth = previousHeight = previousBitdepth = -1;
  previousColor = FALSE;
  first = TRUE;

  {

    // --- default values ---
    w = h = -1;
    layout = LAYOUT_SINGLE;
    layoutStr = "single";
    preRotate = 0;
    postRotate = 0;
    preMirror = 0;
    postMirror = 0;
    preShift[WIDTH] = preShift[HEIGHT] = 0;
    postShift[WIDTH] = postShift[HEIGHT] = 0;
    size[WIDTH] = size[HEIGHT] = -1;
    postSize[WIDTH] = postSize[HEIGHT] = -1;
    stretchSize[WIDTH] = stretchSize[HEIGHT] = -1;
    postStretchSize[WIDTH] = postStretchSize[HEIGHT] = -1;
    zoomFactor = 1.0;
    postZoomFactor = 1.0;
    outputTypeName = NULL; // default derived from input
    outputDepth = -1; // default derived from input
    pointCount = 0;
    maskCount = 0;
    preMaskCount = 0;
    wipeCount = 0;
    preWipeCount = 0;
    postWipeCount = 0;
    middleWipe[0] = middleWipe[1] = 0; // left/right
    border[LEFT] = border[TOP] = border[RIGHT] = border[BOTTOM] = 0;
    preBorder[LEFT] = preBorder[TOP] = preBorder[RIGHT] = preBorder[BOTTOM] = 0;
    postBorder[LEFT] = postBorder[TOP] = postBorder[RIGHT] = postBorder[BOTTOM] = 0;
    blackfilterScanDirections = (1<<HORIZONTAL) | (1<<VERTICAL);
    blackfilterScanSize[HORIZONTAL] = blackfilterScanSize[VERTICAL] = 20;
    blackfilterScanDepth[HORIZONTAL] = blackfilterScanDepth[VERTICAL] = 500;
    blackfilterScanStep[HORIZONTAL] = blackfilterScanStep[VERTICAL] = 5;
    blackfilterScanThreshold = 0.95;
    blackfilterExcludeCount = 0;
    blackfilterIntensity = 20;
    noisefilterIntensity = 4;
    blurfilterScanSize[HORIZONTAL] = blurfilterScanSize[VERTICAL] = 100;
    blurfilterScanStep[HORIZONTAL] = blurfilterScanStep[VERTICAL] = 50;
    blurfilterIntensity = 0.01;
    grayfilterScanSize[HORIZONTAL] = grayfilterScanSize[VERTICAL] = 50;
    grayfilterScanStep[HORIZONTAL] = grayfilterScanStep[VERTICAL] = 20;
    grayfilterThreshold = 0.5;
    maskScanDirections = (1<<HORIZONTAL);
    maskScanSize[HORIZONTAL] = maskScanSize[VERTICAL] = 50;
    maskScanDepth[HORIZONTAL] = maskScanDepth[VERTICAL] = -1;
    maskScanStep[HORIZONTAL] = maskScanStep[VERTICAL] = 5;
    maskScanThreshold[HORIZONTAL] = maskScanThreshold[VERTICAL] = 0.1;
    maskScanMinimum[WIDTH] = maskScanMinimum[HEIGHT] = 100;
    maskScanMaximum[WIDTH] = maskScanMaximum[HEIGHT] = -1; // set default later
    maskColor = pixelValue(WHITE, WHITE, WHITE);
    deskewScanEdges = (1<<LEFT) | (1<<RIGHT);
    deskewScanSize = 1500;
    deskewScanDepth = 0.5;
    deskewScanRange = 5.0;
    deskewScanStep = 0.1;
    deskewScanDeviation = 1.0;
    borderScanDirections = (1<<VERTICAL);
    borderScanSize[HORIZONTAL] = borderScanSize[VERTICAL] = 5;
    borderScanStep[HORIZONTAL] = borderScanStep[VERTICAL] = 5;
    borderScanThreshold[HORIZONTAL] = borderScanThreshold[VERTICAL] = 5;
    borderAlign = 0; // center
    borderAlignMargin[HORIZONTAL] = borderAlignMargin[VERTICAL] = 0; // center
    outsideBorderscanMaskCount = 0;
    whiteThreshold = 0.9;
    blackThreshold = 0.33;
    sheetSize[WIDTH] = sheetSize[HEIGHT] = -1;
    sheetBackground = WHITE;
    writeoutput = TRUE;
    qpixels = TRUE;
    multisheets = TRUE;
    inputCount = 1;
    outputCount = 1;
    inputFileSequenceCount = 0;
    outputFileSequenceCount = 0;
    verbose = VERBOSE_NONE;
    noBlackfilterMultiIndexCount = 0; // 0: allow all, -1: disable all, n: individual entries
    noNoisefilterMultiIndexCount = 0;
    noBlurfilterMultiIndexCount = 0;
    noGrayfilterMultiIndexCount = 0;
    noMaskScanMultiIndexCount = 0;
    noMaskCenterMultiIndexCount = 0;
    noDeskewMultiIndexCount = 0;
    noWipeMultiIndexCount = 0;
    noBorderMultiIndexCount = 0;
    noBorderScanMultiIndexCount = 0;
    noBorderAlignMultiIndexCount = 0;
    sheetMultiIndexCount = -1; // default: process all between start-sheet and end-sheet
    excludeMultiIndexCount = 0;
    ignoreMultiIndexCount = 0;
    insertBlankCount = 0;
    replaceBlankCount = 0;
    overwrite = FALSE;
    showTime = FALSE;
    dpi = 300;


    // -------------------------------------------------------------------
    // --- parse parameters                                            ---
    // -------------------------------------------------------------------

    i = 1;


    // -------------------------------------------------------------------
    // --- begin processing                                            ---
    // -------------------------------------------------------------------

    if (first)
      {
        if (startSheet < nr)   // startSheet==0
          {
            nr = startSheet;
          }
        first = FALSE;
      }

    if ( nr == startSheet )
      {

        if (startInput == -1)
          {
            startInput = (startSheet - 1) * inputCount + 1;
          }
        if (startOutput == -1)
          {
            startOutput = (startSheet - 1) * outputCount + 1;
          }

        inputNr = startInput;
        outputNr = startOutput;
      }

    // showTime |= (verbose >= VERBOSE_DEBUG); // always show processing time in verbose-debug mode

    // get filenames
    inputFileSequence[0] = "";
    inputFileSequenceCount = 1;

    outputFileSequence[0] = "";
    outputFileSequenceCount = 1;


    // resolve filenames for current sheet
    anyWildcards = FALSE;
    allInputFilesMissing = TRUE;
    blankCount = 0;
    {
      // at least one input page file exists


      // ---------------------------------------------------------------
      // --- process single sheet                                    ---
      // ---------------------------------------------------------------

      if (isInMultiIndex(nr, sheetMultiIndex, sheetMultiIndexCount) && (!isInMultiIndex(nr, excludeMultiIndex, excludeMultiIndexCount)))
        {



          // load input image(s)
          success = TRUE;

          //  success = loadImage(inputFilenamesResolved[j], &page, &inputType);

          fromImageToStruct(picture,&page,&inputType);

          inputTypeName = (char*)FILETYPE_NAMES[inputType];
          inputTypeNames[j] = inputTypeName;

          if (!success)
            {
              exitCode = 2;
            }
          else
            {
              // pre-rotate
              if (preRotate != 0)
                {

                  if (preRotate == 90)
                    {
                      flipRotate(1, &page);
                    }
                  else if (preRotate == -90)
                    {
                      flipRotate(-1, &page);
                    }
                }

              // if sheet-size is not known yet (and not forced by --sheet-size), set now based on size of (first) input image
              if ( w == -1 )
                {
                  if ( sheetSize[WIDTH] != -1 )
                    {
                      w = sheetSize[WIDTH];
                    }
                  else
                    {
                      w = page.width * inputCount;
                    }
                }
              if ( h == -1 )
                {
                  if ( sheetSize[HEIGHT] != -1 )
                    {
                      h = sheetSize[HEIGHT];
                    }
                  else
                    {
                      h = page.height;
                    }
                }
            }


          // place image into sheet buffer
          if ( (inputCount == 1) && (page.buffer != NULL) && (page.width == w) && (page.height == h) )   // quick case: single input file == whole sheet
            {
              sheet.buffer = page.buffer;
              sheet.bufferGrayscale = page.bufferGrayscale;
              sheet.bufferLightness = page.bufferLightness;
              sheet.bufferDarknessInverse = page.bufferDarknessInverse;
              sheet.width = page.width;
              sheet.height = page.height;
              sheet.bitdepth = page.bitdepth;
              sheet.color = page.color;
              sheet.background = sheetBackground;
            }
          else     // generic case: place image onto sheet by copying
            {
              // allocate sheet-buffer if not done yet
              if ((sheet.buffer == NULL) && (w != -1) && (h != -1))
                {
                  if ((page.buffer != NULL) && (page.bitdepth != 0))
                    {
                      bd = page.bitdepth;
                      col = page.color;
                    }
                  else
                    {
                      if (outputDepth != -1)   // set via --depth
                        {
                          bd = outputDepth;
                        }
                      else
                        {
                          // bd remains default
                        }
                    }
                  initImage(&sheet, w, h, bd, col, sheetBackground);

                }
              else if ((page.buffer != NULL) && ((page.bitdepth > sheet.bitdepth) || ( (!sheet.color) && page.color )))     // make sure current sheet buffer has enough bitdepth and color-mode
                {
                  sheetBackup = sheet;
                  // re-allocate sheet
                  bd = page.bitdepth;
                  col = page.color;
                  initImage(&sheet, w, h, bd, col, sheetBackground);
                  // copy old one
                  copyImage(&sheetBackup, 0, 0, &sheet);
                  freeImage(&sheetBackup);
                }
              if (page.buffer != NULL)
                {

                  centerImage(&page, (w * j / inputCount), 0, (w / inputCount), h, &sheet);

                  freeImage(&page);
                }
            }


          // the only case that buffer is not yet initialized is if all blank pages have been inserted
          if (sheet.buffer == NULL)
            {
              // last chance: try to get previous (unstretched/not zoomed) sheet size
              w = previousWidth;
              h = previousHeight;
              bd = previousBitdepth;
              col = previousColor;

              if ((w == -1) || (h == -1))
                {
                  return 2;
                }
              else
                {
                  initImage(&sheet, w, h, bd, col, sheetBackground);
                }
            }

          if (success)   // sheet loaded successfully, size is known
            {

              previousWidth = w;
              previousHeight = h;
              previousBitdepth = bd;
              previousColor = col;
              // handle file types
              if (outputTypeName == NULL)   // auto-set output type according to sheet format, if not explicitly set by user
                {
                  if (sheet.color)
                    {
                      outputType = PPM;
                    }
                  else
                    {
                      if (sheet.bitdepth == 1)
                        {
                          outputType = PBM;
                        }
                      else
                        {
                          outputType = PGM;
                        }
                    }
                  outputTypeName = (char*)FILETYPE_NAMES[outputType];
                }
              else     // parse user-setting
                {
                  outputType = -1;
                  for (i = 0; (outputType == -1) && (i < FILETYPES_COUNT); i++)
                    {
                      if (strcmp(outputTypeName, FILETYPE_NAMES[i])==0)
                        {
                          outputType = i;
                        }
                    }
                  if (outputType == -1)
                    {
                      return 2;
                    }
                }
              if (outputDepth == -1)   // set output depth to be as input depth, if not explicitly set by user
                {
                  outputDepth = sheet.bitdepth;
                }

              if (showTime)
                {
                  startTime = clock();
                }

              // pre-mirroring
              if (preMirror != 0)
                {
                  mirror(preMirror, &sheet);
                }

              // pre-shifting
              if ((preShift[WIDTH] != 0) || ((preShift[HEIGHT] != 0)))
                {
                  shift(preShift[WIDTH], preShift[HEIGHT], &sheet);
                }

              // pre-masking
              if (preMaskCount > 0)
                {
                  applyMasks(preMask, preMaskCount, maskColor, &sheet);
                }


              // -------------------------------------------------------
              // --- process image data                              ---
              // -------------------------------------------------------

              // stretch
              if ((stretchSize[WIDTH] != -1) || (stretchSize[HEIGHT] != -1))
                {
                  if (stretchSize[WIDTH] != -1)
                    {
                      w = stretchSize[WIDTH];
                    }
                  else
                    {
                      w = sheet.width;
                    }
                  if (stretchSize[HEIGHT] != -1)
                    {
                      h = stretchSize[HEIGHT];
                    }
                  else
                    {
                      h = sheet.height;
                    }
                  stretch(w, h, &sheet);
                }

              // zoom
              if (zoomFactor != 1.0)
                {
                  w = sheet.width * zoomFactor;
                  h = sheet.height * zoomFactor;
                  stretch(w, h, &sheet);
                }

              // size
              if ((size[WIDTH] != -1) || (size[HEIGHT] != -1))
                {
                  if (size[WIDTH] != -1)
                    {
                      w = size[WIDTH];
                    }
                  else
                    {
                      w = sheet.width;
                    }
                  if (size[HEIGHT] != -1)
                    {
                      h = size[HEIGHT];
                    }
                  else
                    {
                      h = sheet.height;
                    }
                  resize(w, h, &sheet);
                }


              // handle sheet layout

              // LAYOUT_SINGLE
              if (layout == LAYOUT_SINGLE)
                {
                  // This affect horizontal displacement
                  // set middle of sheet as single starting point for mask detection
                  if (pointCount == 0)   // no manual settings, use auto-values
                    {
                      point[pointCount][X] = sheet.width / 2;
                      point[pointCount][Y] = sheet.height / 2;
                      pointCount++;
                    }

                  if (maskScanMaximum[WIDTH] == -1)
                    {
                      maskScanMaximum[WIDTH] = sheet.width;
                    }
                  if (maskScanMaximum[HEIGHT] == -1)
                    {
                      maskScanMaximum[HEIGHT] = sheet.height;
                    }
                  // avoid inner half of the sheet to be blackfilter-detectable

                  if (blackfilterExcludeCount == 0)   // no manual settings, use auto-values
                    {
                      blackfilterExclude[blackfilterExcludeCount][LEFT] = sheet.width / 4;
                      blackfilterExclude[blackfilterExcludeCount][TOP] = sheet.height / 4;
                      blackfilterExclude[blackfilterExcludeCount][RIGHT] = sheet.width / 2 + sheet.width / 4;
                      blackfilterExclude[blackfilterExcludeCount][BOTTOM] = sheet.height / 2 + sheet.height / 4;
                      blackfilterExcludeCount++;
                    }
                  // This affects vertical displacement
                  // set single outside border to start scanning for final border-scan
                  if (outsideBorderscanMaskCount == 0)   // no manual settings, use auto-values
                    {
                      outsideBorderscanMaskCount = 1;
                      outsideBorderscanMask[0][LEFT] = 0;
                      outsideBorderscanMask[0][RIGHT] = sheet.width - 1;
                      outsideBorderscanMask[0][TOP] = 0;
                      outsideBorderscanMask[0][BOTTOM] = sheet.height - 1;
                    }

                  // LAYOUT_DOUBLE
                }
              else if (layout == LAYOUT_DOUBLE)
                {
                  // set two middle of left/right side of sheet as starting points for mask detection
                  if (pointCount == 0)   // no manual settings, use auto-values
                    {
                      point[pointCount][X] = sheet.width / 4;
                      point[pointCount][Y] = sheet.height / 2;
                      pointCount++;
                      point[pointCount][X] = sheet.width - sheet.width / 4;
                      point[pointCount][Y] = sheet.height / 2;
                      pointCount++;
                    }
                  if (maskScanMaximum[WIDTH] == -1)
                    {
                      maskScanMaximum[WIDTH] = sheet.width / 2;
                    }
                  if (maskScanMaximum[HEIGHT] == -1)
                    {
                      maskScanMaximum[HEIGHT] = sheet.height;
                    }
                  if (middleWipe[0] > 0 || middleWipe[1] > 0)   // left, right
                    {
                      wipe[wipeCount][LEFT] = sheet.width / 2 - middleWipe[0];
                      wipe[wipeCount][TOP] = 0;
                      wipe[wipeCount][RIGHT] =  sheet.width / 2 + middleWipe[1];
                      wipe[wipeCount][BOTTOM] = sheet.height - 1;
                      wipeCount++;
                    }
                  // avoid inner half of each page to be blackfilter-detectable
                  if (blackfilterExcludeCount == 0)   // no manual settings, use auto-values
                    {
                      blackfilterExclude[blackfilterExcludeCount][LEFT] = sheet.width / 8;
                      blackfilterExclude[blackfilterExcludeCount][TOP] = sheet.height / 4;
                      blackfilterExclude[blackfilterExcludeCount][RIGHT] = sheet.width / 4 + sheet.width / 8;
                      blackfilterExclude[blackfilterExcludeCount][BOTTOM] = sheet.height / 2 + sheet.height / 4;
                      blackfilterExcludeCount++;
                      blackfilterExclude[blackfilterExcludeCount][LEFT] = sheet.width / 2 + sheet.width / 8;
                      blackfilterExclude[blackfilterExcludeCount][TOP] = sheet.height / 4;
                      blackfilterExclude[blackfilterExcludeCount][RIGHT] = sheet.width / 2 + sheet.width / 4 + sheet.width / 8;
                      blackfilterExclude[blackfilterExcludeCount][BOTTOM] = sheet.height / 2 + sheet.height / 4;
                      blackfilterExcludeCount++;
                    }
                  // set two outside borders to start scanning for final border-scan
                  if (outsideBorderscanMaskCount == 0)   // no manual settings, use auto-values
                    {
                      outsideBorderscanMaskCount = 2;
                      outsideBorderscanMask[0][LEFT] = 0;
                      outsideBorderscanMask[0][RIGHT] = sheet.width / 2;
                      outsideBorderscanMask[0][TOP] = 0;
                      outsideBorderscanMask[0][BOTTOM] = sheet.height - 1;
                      outsideBorderscanMask[1][LEFT] = sheet.width / 2;
                      outsideBorderscanMask[1][RIGHT] = sheet.width - 1;
                      outsideBorderscanMask[1][TOP] = 0;
                      outsideBorderscanMask[1][BOTTOM] = sheet.height - 1;
                    }
                }

              // if maskScanMaximum still unset (no --layout specified), set to full sheet size now
              if (maskScanMinimum[WIDTH] == -1)
                {
                  maskScanMaximum[WIDTH] = sheet.width;
                }
              if (maskScanMinimum[HEIGHT] == -1)
                {
                  maskScanMaximum[HEIGHT] = sheet.height;
                }


              // pre-wipe
              if (!isExcluded(nr, noWipeMultiIndex, noWipeMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  applyWipes(preWipe, preWipeCount, maskColor, &sheet);
                }

              // pre-border
              if (!isExcluded(nr, noBorderMultiIndex, noBorderMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  applyBorder(preBorder, maskColor, &sheet);
                }

              // black area filter
              if (!isExcluded(nr, noBlackfilterMultiIndex, noBlackfilterMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  saveDebug("./_before-blackfilter.pnm", &sheet);
                  blackfilter(blackfilterScanDirections, blackfilterScanSize, blackfilterScanDepth, blackfilterScanStep, blackfilterScanThreshold, blackfilterExclude, blackfilterExcludeCount, blackfilterIntensity, blackThreshold, &sheet);
                  saveDebug("./_after-blackfilter.pnm", &sheet);
                }

              // noise filter
              if (!isExcluded(nr, noNoisefilterMultiIndex, noNoisefilterMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  filterResult = noisefilter(noisefilterIntensity, whiteThreshold, &sheet);
                }

              // blur filter
              if (!isExcluded(nr, noBlurfilterMultiIndex, noBlurfilterMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  filterResult = blurfilter(blurfilterScanSize, blurfilterScanStep, blurfilterIntensity, whiteThreshold, &sheet);
                }

              // mask-detection
              if (!isExcluded(nr, noMaskScanMultiIndex, noMaskScanMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  maskCount = detectMasks(mask, maskValid, point, pointCount, maskScanDirections, maskScanSize, maskScanDepth, maskScanStep, maskScanThreshold, maskScanMinimum, maskScanMaximum, &sheet);
                }

              /* seems to remove borders when not necessary!!!
                          // permamently apply masks
                          if (maskCount > 0) {
                              applyMasks(mask, maskCount, maskColor, &sheet);
                          }
              */
              // gray filter
              if (!isExcluded(nr, noGrayfilterMultiIndex, noGrayfilterMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  filterResult = grayfilter(grayfilterScanSize, grayfilterScanStep, grayfilterThreshold, blackThreshold, &sheet);
                }

              // rotation-detection
              if ((!isExcluded(nr, noDeskewMultiIndex, noDeskewMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount)))
                {
                  originalSheet = sheet; // copy struct entries ('clone')
                  // convert to qpixels
                  if (qpixels==TRUE)
                    {
                      initImage(&qpixelSheet, sheet.width * 2, sheet.height * 2, sheet.bitdepth, sheet.color, sheetBackground);
                      convertToQPixels(&sheet, &qpixelSheet);
                      sheet = qpixelSheet;
                      q = 2; // qpixel-factor for coordinates in both directions
                    }
                  else
                    {
                      q = 1;
                    }


                  // detect masks again, we may get more precise results now after first masking and grayfilter
                  if (!isExcluded(nr, noMaskScanMultiIndex, noMaskScanMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                    {
                      maskCount = detectMasks(mask, maskValid, point, pointCount, maskScanDirections, maskScanSize, maskScanDepth, maskScanStep, maskScanThreshold, maskScanMinimum, maskScanMaximum, &originalSheet);
                    }


                  // auto-deskew each mask
                  for (i = 0; i < maskCount; i++)
                    {

                      // if ( maskValid[i] == TRUE ) { // point may have been invalidated if mask has not been auto-detected

                      // for rotation detection, original buffer is used (not qpixels)
                      saveDebug("./_before-deskew-detect.pnm", &originalSheet);
                      radians = 0;
                      rotation = - detectRotation(deskewScanEdges, deskewScanRange, deskewScanStep, deskewScanSize, deskewScanDepth, deskewScanDeviation, mask[i][LEFT], mask[i][TOP], mask[i][RIGHT], mask[i][BOTTOM], &originalSheet);
                      saveDebug("./_after-deskew-detect.pnm", &originalSheet);

                      if (rotation != 0.0)
                        {
                          initImage(&rect, (mask[i][RIGHT]-mask[i][LEFT]+1)*q, (mask[i][BOTTOM]-mask[i][TOP]+1)*q, sheet.bitdepth, sheet.color, sheetBackground);
                          initImage(&rectTarget, rect.width, rect.height, sheet.bitdepth, sheet.color, sheetBackground);

                          // copy area to rotate into rSource
                          copyImageArea(mask[i][LEFT]*q, mask[i][TOP]*q, rect.width, rect.height, &sheet, 0, 0, &rect);

                          radians = degreesToRadians(rotation);
                          // rotate
                          rotate(radians, &rect, &rectTarget);

                          // copy result back into whole image
                          copyImageArea(0, 0, rectTarget.width, rectTarget.height, &rectTarget, mask[i][LEFT]*q, mask[i][TOP]*q, &sheet);

                          freeImage(&rect);
                          freeImage(&rectTarget);
                        }
                    }

                  // convert back from qpixels
                  if (qpixels == TRUE)
                    {
                      convertFromQPixels(&qpixelSheet, &originalSheet);
                      freeImage(&qpixelSheet);
                      sheet = originalSheet;
                    }
                }


              // auto-center masks on either single-page or double-page layout
              if ( (!isExcluded(nr, noMaskCenterMultiIndex, noMaskCenterMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount)) && (layout != LAYOUT_NONE) && (maskCount == pointCount) )   // (maskCount==pointCount to make sure all masks had correctly been detected)
                {
                  // perform auto-masking again to get more precise masks after rotation
                  if (!isExcluded(nr, noMaskScanMultiIndex, noMaskScanMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                    {
                      maskCount = detectMasks(mask, maskValid, point, pointCount, maskScanDirections, maskScanSize, maskScanDepth, maskScanStep, maskScanThreshold, maskScanMinimum, maskScanMaximum, &sheet);
                    }


                  // center masks on the sheet, according to their page position
                  for (i = 0; i < maskCount; i++)
                    {
                      int dx=0;
                      int dy=0;
                      centerMask(point[i][X], point[i][Y], mask[i][LEFT], mask[i][TOP], mask[i][RIGHT], mask[i][BOTTOM], &sheet, dx, dy);
                      unpaper_dx +=dx;
                      unpaper_dy +=dy;
                    }
                }

              // explicit wipe
              if (!isExcluded(nr, noWipeMultiIndex, noWipeMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  applyWipes(wipe, wipeCount, maskColor, &sheet);
                }

              // explicit border
              if (!isExcluded(nr, noBorderMultiIndex, noBorderMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  applyBorder(border, maskColor, &sheet);
                }

              // border-detection
              if (!isExcluded(nr, noBorderScanMultiIndex, noBorderScanMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  for (i = 0; i < outsideBorderscanMaskCount; i++)
                    {
                      detectBorder(autoborder[i], borderScanDirections, borderScanSize, borderScanStep, borderScanThreshold, blackThreshold, outsideBorderscanMask[i], &sheet);
                      borderToMask(autoborder[i], autoborderMask[i], &sheet);
                    }
                  applyMasks(autoborderMask, outsideBorderscanMaskCount, maskColor, &sheet);
                  for (i = 0; i < outsideBorderscanMaskCount; i++)
                    {
                      // border-centering
                      if (!isExcluded(nr, noBorderAlignMultiIndex, noBorderAlignMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                        {
                          int dx=0;
                          int dy=0;
                          alignMask(autoborderMask[i], outsideBorderscanMask[i], borderAlign, borderAlignMargin, &sheet,dx,dy);
                          unpaper_dx +=dx;
                          unpaper_dy +=dy;
                        }
                    }
                }

              // post-wipe
              if (!isExcluded(nr, noWipeMultiIndex, noWipeMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  applyWipes(postWipe, postWipeCount, maskColor, &sheet);
                }

              // post-border
              if (!isExcluded(nr, noBorderMultiIndex, noBorderMultiIndexCount, ignoreMultiIndex, ignoreMultiIndexCount))
                {
                  applyBorder(postBorder, maskColor, &sheet);
                }

              // post-mirroring
              if (postMirror != 0)
                {
                  mirror(postMirror, &sheet);
                }

              // post-shifting
              if ((postShift[WIDTH] != 0) || ((postShift[HEIGHT] != 0)))
                {
                  shift(postShift[WIDTH], postShift[HEIGHT], &sheet);
                }

              // post-rotating
              if (postRotate != 0)
                {
                  if (postRotate == 90)
                    {
                      flipRotate(1, &sheet);
                    }
                  else if (postRotate == -90)
                    {
                      flipRotate(-1, &sheet);
                    }
                }

              // post-stretch
              if ((postStretchSize[WIDTH] != -1) || (postStretchSize[HEIGHT] != -1))
                {
                  if (postStretchSize[WIDTH] != -1)
                    {
                      w = postStretchSize[WIDTH];
                    }
                  else
                    {
                      w = sheet.width;
                    }
                  if (postStretchSize[HEIGHT] != -1)
                    {
                      h = postStretchSize[HEIGHT];
                    }
                  else
                    {
                      h = sheet.height;
                    }
                  stretch(w, h, &sheet);
                }

              // post-zoom
              if (postZoomFactor != 1.0)
                {
                  w = sheet.width * postZoomFactor;
                  h = sheet.height * postZoomFactor;
                  stretch(w, h, &sheet);
                }

              // post-size
              if ((postSize[WIDTH] != -1) || (postSize[HEIGHT] != -1))
                {
                  if (postSize[WIDTH] != -1)
                    {
                      w = postSize[WIDTH];
                    }
                  else
                    {
                      w = sheet.width;
                    }
                  if (postSize[HEIGHT] != -1)
                    {
                      h = postSize[HEIGHT];
                    }
                  else
                    {
                      h = sheet.height;
                    }
                  resize(w, h, &sheet);
                }

              if (showTime)
                {
                  endTime = clock();
                }

              // --- write output file ---

              // write split pages output

              if (writeoutput == TRUE)
                {
                  success = TRUE;
                  page.width = sheet.width / outputCount;
                  page.height = sheet.height;
                  page.bitdepth = sheet.bitdepth;
                  page.color = sheet.color;
                  for ( j = 0; success && (j < outputCount); j++)
                    {
                      // get pagebuffer
                      if ( outputCount == 1 )
                        {
                          page.buffer = sheet.buffer;
                          page.bufferGrayscale = sheet.bufferGrayscale;
                          page.bufferLightness = sheet.bufferLightness;
                          page.bufferDarknessInverse = sheet.bufferDarknessInverse;
                        }
                      else     // generic case: copy page-part of sheet into own buffer
                        {
                          if (page.color)
                            {
                              page.buffer = (unsigned char*)malloc( page.width * page.height * 3 );
                              page.bufferGrayscale = (unsigned char*)malloc( page.width * page.height );
                              page.bufferLightness = (unsigned char*)malloc( page.width * page.height );
                              page.bufferDarknessInverse = (unsigned char*)malloc( page.width * page.height );
                            }
                          else
                            {
                              page.buffer = (unsigned char*)malloc( page.width * page.height );
                              page.bufferGrayscale = page.buffer;
                              page.bufferLightness = page.buffer;
                              page.bufferDarknessInverse = page.buffer;
                            }
                          copyImageArea(page.width * j, 0, page.width, page.height, &sheet, 0, 0, &page);
                        }

                      //success = saveImage(outputFilenamesResolved[j], &page, outputType, overwrite, blackThreshold);

                      fromStructToImage(picture,&page);

                      if ( outputCount > 1 )
                        {
                          freeImage(&page);
                        }
                      if (success == FALSE)
                        {
                          exitCode = 2;
                        }
                    }
                }

              freeImage(&sheet);
              sheet.buffer = NULL;

              if (showTime)
                {
                  if (startTime > endTime)   // clock overflow
                    {
                      endTime -= startTime; // "re-underflow" value again
                      startTime = 0;
                    }
                  time = endTime - startTime;
                  totalTime += time;
                  totalCount++;
                }
            }
        }
    }
  }

  return exitCode;
}
