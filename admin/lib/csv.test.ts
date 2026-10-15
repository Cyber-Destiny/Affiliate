import { test } from 'node:test'
import assert from 'node:assert/strict'
import { matchCategory, normaliseHeader, planImport, slugFromFilename, splitTags } from './csv.ts'

const categories = ['Laughing', 'Vex', 'Shock', 'Leave me alone', 'Praise', 'Broke', 'Love', 'Sarcasm']

test('slugs come from file names', () => {
  assert.equal(slugFromFilename('leave-me-alone.mp4'), 'leave-me-alone')
  assert.equal(slugFromFilename('clips/mp3/Leave Me Alone (1).MP3'), 'leave-me-alone-1')
  assert.equal(slugFromFilename("Don't vex"), 'dont-vex')
  assert.equal(slugFromFilename('geh-geh'), 'geh-geh')
})

test('tags split on commas, semicolons and pipes without duplicates', () => {
  assert.deepEqual(splitTags('leave me alone, comot;go away | Comot ,, '), ['leave me alone', 'comot', 'go away'])
  assert.deepEqual(splitTags(undefined), [])
})

test('categories match loosely', () => {
  assert.equal(matchCategory('leave-me-alone', categories), 'Leave me alone')
  assert.equal(matchCategory(' LAUGHING ', categories), 'Laughing')
  assert.equal(matchCategory('Happy', categories), null)
  assert.equal(matchCategory('', categories), null)
})

test('headers are normalised', () => {
  assert.equal(normaliseHeader(' Filename '), 'filename')
  assert.equal(normaliseHeader('File Name'), 'filename')
  assert.equal(normaliseHeader('Source URL'), 'source_url')
})

test('plan pairs CSV rows with their MP4 and MP3', () => {
  const plan = planImport(
    [
      { filename: 'leave-me-alone.mp4', title: 'Leave me alone', category: 'leave me alone', tags: 'comot, go away' },
      { filename: 'geh-geh', title: '', category: 'Shock', tags: 'geh' },
      { filename: 'no-audio', title: 'No audio', category: 'Vex' },
      { filename: 'leave-me-alone', title: 'Again', category: 'Vex' },
      { filename: 'bad-cat', title: 'Bad', category: 'Happy' },
    ],
    [
      'mp4/leave-me-alone.mp4', 'mp3/leave-me-alone.mp3',
      'geh-geh.mp4', 'geh-geh.mp3',
      'no-audio.mp4',
      'bad-cat.mp4', 'bad-cat.mp3',
      'extra.mp4', 'extra.mp3', 'clips.csv',
    ],
    categories,
  )

  const [leave, geh, noAudio, dup, badCat] = plan.items
  assert.deepEqual(leave, {
    line: 2, slug: 'leave-me-alone', title: 'Leave me alone', category: 'Leave me alone',
    tags: ['comot', 'go away'], mp4: 'mp4/leave-me-alone.mp4', mp3: 'mp3/leave-me-alone.mp3', problems: [],
  })
  assert.equal(geh.title, 'Geh geh')
  assert.deepEqual(geh.problems, [])
  assert.deepEqual(noAudio.problems, ['Missing no-audio.mp3'])
  assert.deepEqual(dup.problems, ['Same filename used twice in the CSV'])
  assert.deepEqual(badCat.problems, ['Unknown category “Happy”'])
  assert.deepEqual(plan.unusedFiles, ['extra.mp3', 'extra.mp4'])
})
