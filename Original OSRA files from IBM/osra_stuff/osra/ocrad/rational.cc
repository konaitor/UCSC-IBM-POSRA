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

#include <algorithm>
#include <cctype>
#include <climits>
#include <cstdlib>
#include <string>

#include "rational.h"


namespace {

int gcd( int n, int m ) throw()		// Greatest Common Divisor
  {
  if( n < 0 ) n = -n;
  if( m < 0 ) m = -m;

  while( true )
    {
    if( m ) n %= m; else return n;
    if( n ) m %= n; else return m;
    }
  }


int lcm( int n, int m ) throw()		// Least Common Multiple
  {
  if( !n || !m ) return 0;

  n /= gcd( n, m ); n *= m;	// lcm( n, m ) == ( n * m ) / gcd( n, m )
  if( n < 0 ) n = -n;
  return n;
  }

} // end namespace


void Rational::normalize() throw()
  {
  if( num == 0 ) { den = 1; return; }
  if( den == 0 )
    { den = 1; if( num > 0 ) num = INT_MAX; else num = INT_MIN; return; }
  if( den < 0 ) { num = -num; den = -den; }

  if( den != 1 )
    {
    const int tmp = gcd( num, den );
    num /= tmp; den /= tmp;
    }
  }


Rational & Rational::operator+=( const Rational & r ) throw()
  {
  const int tmp = lcm( den, r.den );
  num = ( ( tmp / den ) * num ) + ( ( tmp / r.den ) * r.num );
  den = tmp;
  return *this;
  }


Rational & Rational::operator*=( const Rational & r ) throw()
  {
  const int tmp1 = gcd( num, r.den );
  const int tmp2 = gcd( r.num, den );
  num = ( num / tmp1 ) * ( r.num / tmp2 );
  den = ( den / tmp2 ) * ( r.den / tmp1 );
  normalize();				// overflow may break the invariant
  return *this;
  }


Rational & Rational::operator/=( const Rational & r ) throw()
  {
  if( num )
    {
    if( r.num == 0 ) den = 0;
    else
      {
      const int tmp1 = gcd( num, r.num );
      const int tmp2 = gcd( den, r.den );
      num = ( num / tmp1 ) * ( r.den / tmp2 );
      den = ( den / tmp2 ) * ( r.num / tmp1 );
      }
    }
  normalize();
  return *this;
  }


bool Rational::operator<( const Rational & r ) const throw()
  {
  if( num >= 0 && r.num <= 0 ) return false;
  if( ( num <= 0 && r.num > 0 ) || ( num < 0 && r.num >= 0 ) ) return true;

  int tmp1 = num / den, tmp2 = r.num / r.den;	// both values have same sign
  if( tmp1 != tmp2 ) return ( tmp1 < tmp2 );

  tmp1 = gcd( num, r.num );		// values differ by less than 1
  tmp2 = gcd( r.den, den );
  return ( ( num / tmp1 ) * ( r.den / tmp2 ) < ( den / tmp2 ) * ( r.num / tmp1 ) );
  }


Rational Rational::inverse() const throw()
  {
  Rational tmp( den );
  if( num == 0 ) { tmp.num = INT_MAX; tmp.den = 1; }
  else if( num < 0 ) { tmp.num = -den; tmp.den = -num; }
  else tmp.den = num;
  return tmp;
  }


int Rational::round() const throw()
  {
  int result = num / den, rest = std::abs( num ) % den;
  if( rest > 0 && rest >= den - rest )
    { if( num >= 0 ) ++result; else --result; }
  return result;
  }


// Recognized formats: 123 123/456 123.456 .123 12% 12/3% 12.3% .12%
// Values may be preceded by an optional '+' or '-' sign.
// Returns the number of chars read, or 0 if error.
//
int Rational::parse( const char * const s ) throw()
  {
  if( !s || !s[0] ) return 0;
  int n = 0, d = 1, c = 0;
  bool minus = false;

  while( std::isspace( s[c] ) ) ++c;
  if( s[c] == '+' ) ++c;
  else if( s[c] == '-' ) { ++c; minus = true; }
  if( !std::isdigit( s[c] ) && s[c] != '.' ) return 0;

  while( std::isdigit( s[c] ) )
    {
    if( ( INT_MAX - (s[c] - '0') ) / 10 < n ) return 0;
    n = (n * 10) + (s[c] - '0'); ++c;
    }

  if( s[c] == '.' )
    {
    ++c; if( !std::isdigit( s[c] ) ) return 0;
    while( std::isdigit( s[c] ) )
      {
      if( ( INT_MAX - (s[c] - '0') ) / 10 < n || INT_MAX / 10 < d ) return 0;
      n = (n * 10) + (s[c] - '0'); d *= 10; ++c;
      }
    }
  else if( s[c] == '/' )
    {
    ++c; d = 0;
    while( std::isdigit( s[c] ) )
      {
      if( ( INT_MAX - (s[c] - '0') ) / 10 < d ) return 0;
      d = (d * 10) + (s[c] - '0'); ++c;
      }
    if( d == 0 ) return 0;
    }

  if( s[c] == '%' )
    {
    ++c;
    if( n % 100 == 0 ) n /= 100;
    else if( n % 10 == 0 && INT_MAX / 10 >= d ) { n /= 10; d *= 10; }
    else if( INT_MAX / 100 >= d ) d *= 100;
    else return 0;
    }

  if( minus ) n = -n;
  num = n; den = d; normalize();
  return c;
  }


// Returns the fraction "num/den" as a floating point with "prec" decimals.
// If 'prec' is negative, only the needed decimals are shown.
//
const std::string Rational::to_decimal( const unsigned int iwidth, int prec ) const
  {
  std::string s;

  if( den == 0 )
    {
    if( num == 0 ) s = "NAN"; else if( num > 0 ) s = "+INF"; else s = "-INF";
    return s;
    }
  bool negative = false, trunc = false;
  int ipart = num / den;
  if( ipart < 0 ) { ipart = -ipart; negative = true; }
  if( prec < 0 ) { prec = -prec; trunc = true; }

  do { s += '0' + ( ipart % 10 ); ipart /= 10; } while( ipart > 0 );
  if( negative ) s += '-';
  if( iwidth > s.size() ) s.append( iwidth - s.size(), ' ' );
  std::reverse( s.begin(), s.end() );
  long long rest = std::abs( num ) % den;
  if( prec > 0 && ( rest > 0 || !trunc ) )
    {
    s += '.';
    while( prec > 0 && ( rest > 0 || !trunc ) )
      { rest *= 10; s += '0' + ( rest / den ); rest %= den; --prec; }
    }
  return s;
  }
