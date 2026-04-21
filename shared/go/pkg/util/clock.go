package util

import "time"

// Clock abstracts time operations for testability
type Clock interface {
	Now() time.Time
	Since(t time.Time) time.Duration
	Until(t time.Time) time.Duration
}

// RealClock is a Clock implementation that delegates to the standard library
var _ Clock = (*RealClock)(nil)

type RealClock struct{}

func NewRealClock() *RealClock {
	return &RealClock{}
}

func (c *RealClock) Now() time.Time {
	return time.Now()
}

func (c *RealClock) Since(t time.Time) time.Duration {
	return time.Since(t)
}

func (c *RealClock) Until(t time.Time) time.Duration {
	return time.Until(t)
}

// FakeClock is a Clock implementation suitable for use in tests
var _ Clock = (*FakeClock)(nil)

type FakeClock struct {
	time time.Time
}

func NewFakeClock(t time.Time) *FakeClock {
	return &FakeClock{time: t}
}

func (c *FakeClock) Now() time.Time {
	return c.time
}

func (c *FakeClock) Since(t time.Time) time.Duration {
	return c.time.Sub(t)
}

func (c *FakeClock) Until(t time.Time) time.Duration {
	return t.Sub(c.time)
}

// Advance moves the fake clock forward by d
func (c *FakeClock) Advance(d time.Duration) {
	c.time = c.time.Add(d)
}

// Set sets the fake clock to a specific time
func (c *FakeClock) Set(t time.Time) {
	c.time = t
}
