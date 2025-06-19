export const errorText =
  `ERROR: Failed to create playlist!

  Possible reasons:
  \u2022 Couldn't find the setlist on setlist.fm
  \u2022 More than half of the of the songs couldn't be found on Spotify
  \u2022 Spotify's API is currently down (unfortunately, the only option here is to wait...)
  
  If the problem persists or if you believe it's a bug, PLEASE report the issue on GitHub or the setlist.fm forum along with a link to the problematic setlist, and I'll gladly take a look at it.
  
  Thank you!`
    .split('\n').map(line => line.trim()).join('\n');

export const philosophyText =
  `setlist.fm to Spotify – by Selbi

  This tool is a 100% non-profit plaything I coded a couple of years ago to help me organize (and get excited about) concerts again, after COVID-19 completely ruined my enjoyment of music for a while.
  
  However, I've heard concerns from people about privacy, so let me clarify my ambition and philosophy with this project: I HATE modern tech! Google, Meta, Apple, Amazon, Microsoft—these companies have infiltrated our lives to the point where data harvesting isn't just something you "might" do; it's become the universally agreed-upon currency to "unite" us all. But what it's really done is leave us with software designed to keep us glued to platforms instead of providing the actual information we're looking for.
  
  The thing is, I didn't realize just how bad it was until I first created this setlist conversion tool. Starting from scratch, I was curious about how Google's ecosystem (Analytics, AdSense, and the like) worked. What I learned turned my stomach: Google doesn't care about you unless you include their plugins. Seriously, this page was nearly invisible on Google, even if you typed the name out character by character. The moment I added Google's tracking scripts to see how many people were using the tool, I became close to the top-ranking result. Once I got the data I was curious about (about 250 monthly visitors by the end of 2024, for the record), I removed the Google junk again. The result? I immediately dropped down so many ranks that you'd have more luck finding this tool through the setlist.fm forum post than, you know, directly getting the link from Google. And the only way to revert that is re-inserting the Google spyware again. It's ridiculous.
  
  So, I made a decision. I axed Google and any other big tech from this service. I made a solemn vow to never, ever, EVER include ads or data tracking (and if you want to support me in any way, my Ko-fi page is the best way to do so). I believe good software that respects the customer as a FREAKING HUMAN BEING can still exist, we just need to finally embrace it.
  
  And yes, before you say it, I'm fully aware of the irony in boycotting these companies only to make a tool that directly supports Spotify. You can't win 'em all, I guess. That said, I'd argue Spotify is more interested in pushing their AI-artist-infested playlists than caring about a few songs from a concert.
  
  – Selbi (Jan 4, 2025)`;