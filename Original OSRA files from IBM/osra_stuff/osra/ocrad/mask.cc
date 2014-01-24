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

#include <climits>
#include <cstdlib>
#include <vector>

#include "rectangle.h"
#include "mask.h"


void Segment::add_point( const int col ) throw()
  {
  if( !valid() ) left = right = col;
  else if( col < left ) left = col;
  else if( col > right ) right = col;
  }


void Segment::add_segment( const Segment & seg ) throw()
  {
  if( seg.valid() )
    {
    if( !valid() ) *this = seg;
    else
      {
      if( seg.left < left ) left = seg.left;
      if( seg.right > right ) right = seg.right;
      }
    }
  }


int Segment::distance( const Segment & seg ) const throw()
  {
  if( !valid() || !seg.valid() ) return INT_MAX;
  if( seg.right < left ) return left - seg.right;
  if( seg.left > right ) return seg.left - right;
  return 0;
  }


int Segment::distance( const int col ) const throw()
  {
  if( !valid() ) return INT_MAX;
  if( col < left ) return left - col;
  if( col > right ) return col - right;
  return 0;
  }


int Mask::left( const int row ) const throw()
  {
  if( top() <= row && row <= bottom() && data[row-top()].valid() )
    return data[row-top()].left;
  return -1;
  }


int Mask::right( const int row ) const throw()
  {
  if( top() <= row && row <= bottom() && data[row-top()].valid() )
    return data[row-top()].right;
  return -1;
  }


void Mask::top( const int t )
  {
  if( t == top() ) return;
  if( t < top() ) data.insert( data.begin(), top() - t, Segment() );
  else data.erase( data.begin(), data.begin() + ( t - top() ) );
  Rectangle::top( t );
  }


void Mask::bottom( const int b )
  {
  if( b != bottom() ) { Rectangle::bottom( b ); data.resize( height() ); }
  }


void Mask::add_mask( const Mask & m )
  {
  if( m.top() < top() ) top( m.top() );
  if( m.bottom() > bottom() ) bottom( m.bottom() );
  for( int i = m.top(); i <= m.bottom(); ++i )
    {
    Segment & seg = data[i-top()];
    seg.add_segment( m.data[i-m.top()] );
    if( seg.left < left() ) left( seg.left );
    if( seg.right > right() ) right( seg.right );
    }
  }


void Mask::add_point( const int row, const int col )
  {
  if( row < top() ) top( row ); else if( row > bottom() ) bottom( row );
  data[row-top()].add_point( col );
  if( col < left() ) left( col ); else if( col > right() ) right( col );
  }


void Mask::add_rectangle( const Rectangle & re )
  {
  if( re.top() < top() ) top( re.top() );
  if( re.bottom() > bottom() ) bottom( re.bottom() );
  const Segment rseg( re.left(), re.right() );
  for( int i = re.top(); i <= re.bottom(); ++i )
    {
    Segment & seg = data[i-top()];
    seg.add_segment( rseg );
    if( seg.left < left() ) left( seg.left );
    if( seg.right > right() ) right( seg.right );
    }
  }


bool Mask::includes( const Rectangle & re ) const throw()
  {
  if( re.top() < top() || re.bottom() > bottom() ) return false;
  const Segment seg( re.left(), re.right() );
  for( int i = re.top(); i <= re.bottom(); ++i )
    if( !data[i-top()].includes( seg ) ) return false;
  return true;
  }


bool Mask::includes( const int row, const int col ) const throw()
  {
  return ( row >= top() && row <= bottom() && data[row-top()].includes( col ) );
  }


int Mask::distance( const Rectangle & re ) const throw()
  {
  const Segment seg( re.left(), re.right() );
  int mindist = INT_MAX;
  for( int i = bottom(); i >= top(); --i )
    {
    const int vd = re.v_distance( i );
    if( vd >= mindist ) { if( i < re.top() ) break; else continue; }
    const int hd = data[i-top()].distance( seg );
    if( hd >= mindist ) continue;
    const int d = Rectangle::hypoti( hd, vd );
    if( d < mindist ) mindist = d;
    }
  return mindist;
  }


int Mask::distance( const int row, const int col ) const throw()
  {
  int mindist = INT_MAX;
  for( int i = bottom(); i >= top(); --i )
    {
    const int vd = std::abs( i - row );
    if( vd >= mindist ) { if( i < row ) break; else continue; }
    const int hd = data[i-top()].distance( col );
    if( hd >= mindist ) continue;
    const int d = Rectangle::hypoti( hd, vd );
    if( d < mindist ) mindist = d;
    }
  return mindist;
  }
