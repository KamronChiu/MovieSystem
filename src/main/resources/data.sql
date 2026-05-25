-- ============================================================
-- 1. Cinemas
-- Case study cities: Birmingham, Bristol, Cardiff, London
-- Each city has at least two cinemas.
-- ============================================================

MERGE INTO cinemas (id, name, city, address) KEY(id) VALUES
    (1, 'Horizon London Central', 'London', '12 King Street, London'),
    (2, 'Horizon London Riverside', 'London', '45 River Road, London'),
    (3, 'Horizon Birmingham City', 'Birmingham', '8 New Street, Birmingham'),
    (4, 'Horizon Birmingham South', 'Birmingham', '22 Park Lane, Birmingham'),
    (5, 'Horizon Bristol Central', 'Bristol', '6 College Green, Bristol'),
    (6, 'Horizon Bristol Harbour', 'Bristol', '19 Harbour Road, Bristol'),
    (7, 'Horizon Cardiff Bay', 'Cardiff', '5 Bay Avenue, Cardiff'),
    (8, 'Horizon Cardiff North', 'Cardiff', '31 North Road, Cardiff');

-- Keep generated IDs safely above seed IDs.
ALTER TABLE cinemas ALTER COLUMN id RESTART WITH 100;


-- ============================================================
-- 2. Screens
-- Each cinema can have up to 6 screens.
-- Seating capacity should be between 50 and 120.
-- Seats are generated automatically by DataInitializer.java.
-- ============================================================

MERGE INTO screens (id, cinema_id, screen_number, capacity, hall_type) KEY(id) VALUES
    (1, 1, 1, 80, 'IMAX'),
    (2, 1, 2, 60, 'REGULAR'),
    (3, 1, 3, 48, 'PREMIUM'),

    (4, 2, 1, 80, 'IMAX'),
    (5, 2, 2, 70, 'REGULAR'),

    (6, 3, 1, 70, 'REGULAR'),
    (7, 3, 2, 80, 'IMAX'),
    (8, 3, 3, 100, 'REGULAR'),

    (9, 4, 1, 80, 'REGULAR'),
    (10, 4, 2, 48, 'PREMIUM'),

    (11, 5, 1, 60, 'REGULAR'),
    (12, 5, 2, 80, 'IMAX'),

    (13, 6, 1, 48, 'PREMIUM'),
    (14, 6, 2, 100, 'REGULAR'),

    (15, 7, 1, 70, 'REGULAR'),
    (16, 7, 2, 80, 'IMAX'),

    (17, 8, 1, 60, 'REGULAR'),
    (18, 8, 2, 48, 'PREMIUM');

ALTER TABLE screens ALTER COLUMN id RESTART WITH 100;


-- ============================================================
-- 3. Films
-- poster_url files should be placed in:
-- src/main/resources/META-INF/resources/images/posters/
-- ============================================================

MERGE INTO films (
    id,
    title,
    description,
    actors,
    directors,
    genre,
    age_rating,
    duration_minutes,
    release_date,
    content_advice,
    poster_url
    ) KEY(id) VALUES
    (
    1,
    'Project Hail Mary',
    'Science teacher Ryland Grace wakes up on a spacecraft far from Earth with no memory of who he is or how he arrived there. As his memories return, he discovers a mission that could decide the future of humanity and force him to rely on science, courage and an unexpected friendship.',
    'Ryan Gosling, Sandra Hüller, Lionel Boyce, Ken Leung, Milana Vayntrub',
    'Phil Lord, Christopher Miller',
    'Science Fiction',
    '12A',
    156,
    DATE '2026-03-19',
    'Moderate threat, rude humour, drug references, implied strong language',
    '/images/posters/project-hail-mary.jpg'
    ),
    (
    2,
    'Michael',
    'A cinematic portrait of Michael Jackson''s life and legacy, following his journey from the Jackson Five to becoming one of the most influential entertainers in the world.',
    'Jaafar Jackson, Nia Long, Laura Harrier, Juliano Krue Valdi, Miles Teller, Colman Domingo',
    'Antoine Fuqua',
    'Biography',
    '12A',
    127,
    DATE '2026-04-22',
    'Moderate threat, domestic abuse',
    '/images/posters/michael.jpg'
    ),
    (
    3,
    'Star Wars: The Mandalorian and Grogu',
    'The Mandalorian and Grogu begin a new adventure in a galaxy still recovering from the fall of the Empire. As scattered threats remain, the pair are drawn into another mission linked to the future of the New Republic.',
    'Pedro Pascal, Sigourney Weaver, Jeremy Allen White',
    'Jon Favreau',
    'Fantasy',
    '12A',
    132,
    DATE '2026-05-22',
    'Moderate violence, threat, injury detail',
    '/images/posters/star-wars-mandalorian-grogu.jpg'
    ),
    (
    4,
    'Chainsaw Man - The Movie: Reze Arc (Subbed)',
    'Denji returns in a brutal new chapter where love, danger and survival collide. A mysterious girl named Reze enters his world, pulling him into a violent conflict between devils, hunters and hidden enemies.',
    'Shiori Izawa, Reina Ueda, Kikunosuke Toya, Tomori Kusunoki, Shogo Sakata',
    'Tatsuya Yoshihara',
    'Animation',
    '15',
    100,
    DATE '2026-05-26',
    'Strong bloody violence, gore',
    '/images/posters/chainsaw-man-reze-arc-subbed.jpg'
    ),
    (
    5,
    'GOAT',
    'Will, a small goat with big dreams, gets the chance to join a team in the high-intensity sport of roarball. His new teammates doubt him at first, but Will is determined to prove that being small does not mean dreaming small.',
    'Caleb McLaughlin, Gabrielle Union, Stephen Curry, Nicola Coughlan, Nick Kroll, David Harbour, Jennifer Hudson',
    'Tyree Dillihay, Adam Rosette',
    'Animation',
    'PG',
    100,
    DATE '2026-05-15',
    'Mild violence, rude humour, language',
    '/images/posters/goat.jpg'
    ),
    (
    6,
    'The Devil Wears Prada 2',
    'Two decades after their iconic turns in the world of fashion publishing, Miranda, Andy, Emily and Nigel return to the stylish offices and streets connected to Runway Magazine.',
    'Meryl Streep, Anne Hathaway, Emily Blunt, Stanley Tucci',
    'David Frankel',
    'Comedy',
    '12A',
    119,
    DATE '2026-05-01',
    'Infrequent strong language',
    '/images/posters/the-devil-wears-prada-2.jpg'
    ),
    (
    7,
    'Minions',
    'Kevin, Stuart and Bob leave their isolated Minion tribe to search for a new villainous master. Their journey takes them to Scarlet Overkill, a stylish super-villain whose plan soon throws the Minions into comic chaos.',
    'Sandra Bullock, Jon Hamm, Michael Keaton, Allison Janney, Steve Coogan, Jennifer Saunders, Pierre Coffin',
    'Pierre Coffin, Kyle Balda',
    'Animation',
    'U',
    91,
    DATE '2015-07-10',
    'Mild comic violence',
    '/images/posters/minions.jpg'
    ),
    (
    8,
    'Zootopia 2',
    'Rookie cops Judy Hopps and Nick Wilde return for a new case when Gary De''Snake arrives in Zootopia and turns the animal city upside down. To solve the mystery, Judy and Nick must go undercover in unfamiliar parts of the city, testing their partnership in new ways.',
    'Ginnifer Goodwin, Jason Bateman, Ke Huy Quan, Fortune Feimster, Shakira, Idris Elba, Quinta Brunson, Andy Samberg',
    'Jared Bush, Byron Howard',
    'Animation',
    'PG',
    108,
    DATE '2025-11-26',
    'Rude humour, action violence',
    '/images/posters/zootopia-2.jpg'
    );

ALTER TABLE films ALTER COLUMN id RESTART WITH 100;


-- ============================================================
-- 4. Screenings
-- A screening = one film shown on one screen at one date/time.
-- screening_type distinguishes normal public screenings from advance previews.
-- REGULAR_2D: normal public 2D screening on/after release date.
-- REGULAR_3D: normal public 3D screening on/after release date.
-- ADVANCE_PREVIEW_2D: early access 2D screening before the official release date.
-- ADVANCE_PREVIEW_3D: early access 3D screening before the official release date.
-- ============================================================

MERGE INTO screenings (
    id,
    film_id,
    screen_id,
    screening_date,
    start_time,
    end_time,
    screening_type
    ) KEY(id) VALUES
-- Project Hail Mary: mixed 2D and 3D
    (1, 1, 1, DATEADD('DAY', 1, CURRENT_DATE), TIME '10:00:00', TIME '12:36:00', 'REGULAR_2D'),
    (2, 1, 1, DATEADD('DAY', 1, CURRENT_DATE), TIME '14:00:00', TIME '16:36:00', 'REGULAR_3D'),
    (3, 1, 1, DATEADD('DAY', 1, CURRENT_DATE), TIME '17:50:00', TIME '20:26:00', 'REGULAR_2D'),
    (4, 1, 7, DATEADD('DAY', 2, CURRENT_DATE), TIME '15:00:00', TIME '17:36:00', 'REGULAR_3D'),
    (5, 1, 6, DATEADD('DAY', 2, CURRENT_DATE), TIME '20:40:00', TIME '23:16:00', 'REGULAR_2D'),
    (6, 1, 12, DATEADD('DAY', 3, CURRENT_DATE), TIME '18:00:00', TIME '20:36:00', 'REGULAR_3D'),
    (7, 1, 16, DATEADD('DAY', 4, CURRENT_DATE), TIME '19:30:00', TIME '22:06:00', 'REGULAR_2D'),
    (8, 1, 3, DATEADD('DAY', 5, CURRENT_DATE), TIME '21:00:00', TIME '23:36:00', 'REGULAR_3D'),

-- Michael: mixed 2D and 3D
    (9, 2, 2, DATEADD('DAY', 1, CURRENT_DATE), TIME '11:00:00', TIME '13:07:00', 'REGULAR_2D'),
    (10, 2, 2, DATEADD('DAY', 1, CURRENT_DATE), TIME '14:50:00', TIME '16:57:00', 'REGULAR_3D'),
    (11, 2, 11, DATEADD('DAY', 2, CURRENT_DATE), TIME '18:00:00', TIME '20:07:00', 'REGULAR_2D'),
    (12, 2, 15, DATEADD('DAY', 3, CURRENT_DATE), TIME '16:30:00', TIME '18:37:00', 'REGULAR_3D'),
    (13, 2, 15, DATEADD('DAY', 4, CURRENT_DATE), TIME '20:45:00', TIME '22:52:00', 'REGULAR_2D'),
    (14, 2, 8, DATEADD('DAY', 5, CURRENT_DATE), TIME '19:00:00', TIME '21:07:00', 'REGULAR_3D'),

-- Star Wars: The Mandalorian and Grogu: advance preview with 2D/3D
    (15, 3, 7, DATEADD('DAY', 1, CURRENT_DATE), TIME '15:15:00', TIME '17:27:00', 'ADVANCE_PREVIEW_2D'),
    (16, 3, 7, DATEADD('DAY', 1, CURRENT_DATE), TIME '19:00:00', TIME '21:12:00', 'ADVANCE_PREVIEW_3D'),
    (17, 3, 3, DATEADD('DAY', 3, CURRENT_DATE), TIME '18:00:00', TIME '20:12:00', 'ADVANCE_PREVIEW_3D'),
    (18, 3, 13, DATEADD('DAY', 4, CURRENT_DATE), TIME '17:30:00', TIME '19:42:00', 'ADVANCE_PREVIEW_2D'),
    (19, 3, 13, DATEADD('DAY', 5, CURRENT_DATE), TIME '20:45:00', TIME '22:57:00', 'ADVANCE_PREVIEW_3D'),
    (20, 3, 1, DATEADD('DAY', 6, CURRENT_DATE), TIME '19:30:00', TIME '21:42:00', 'ADVANCE_PREVIEW_2D'),

-- Chainsaw Man: advance preview
    (21, 4, 16, DATEADD('DAY', 2, CURRENT_DATE), TIME '17:30:00', TIME '19:10:00', 'ADVANCE_PREVIEW_2D'),
    (22, 4, 16, DATEADD('DAY', 2, CURRENT_DATE), TIME '20:30:00', TIME '22:10:00', 'ADVANCE_PREVIEW_3D'),
    (23, 4, 2, DATEADD('DAY', 4, CURRENT_DATE), TIME '22:00:00', TIME '23:40:00', 'ADVANCE_PREVIEW_2D'),
    (24, 4, 7, DATEADD('DAY', 5, CURRENT_DATE), TIME '21:00:00', TIME '22:40:00', 'ADVANCE_PREVIEW_3D'),

-- GOAT: mix of advance preview and regular screenings
    (25, 5, 11, DATEADD('DAY', 1, CURRENT_DATE), TIME '10:30:00', TIME '12:10:00', 'ADVANCE_PREVIEW_2D'),
    (26, 5, 15, DATEADD('DAY', 2, CURRENT_DATE), TIME '14:00:00', TIME '15:40:00', 'REGULAR_2D'),
    (27, 5, 15, DATEADD('DAY', 3, CURRENT_DATE), TIME '13:20:00', TIME '15:00:00', 'REGULAR_3D'),
    (28, 5, 6, DATEADD('DAY', 4, CURRENT_DATE), TIME '16:00:00', TIME '17:40:00', 'REGULAR_2D'),
    (29, 5, 11, DATEADD('DAY', 5, CURRENT_DATE), TIME '11:00:00', TIME '12:40:00', 'REGULAR_3D'),
    (30, 5, 3, DATEADD('DAY', 6, CURRENT_DATE), TIME '15:30:00', TIME '17:10:00', 'REGULAR_2D'),

-- The Devil Wears Prada 2
    (31, 6, 2, DATEADD('DAY', 1, CURRENT_DATE), TIME '15:30:00', TIME '17:29:00', 'REGULAR_2D'),
    (32, 6, 7, DATEADD('DAY', 2, CURRENT_DATE), TIME '18:30:00', TIME '20:29:00', 'REGULAR_3D'),
    (33, 6, 13, DATEADD('DAY', 3, CURRENT_DATE), TIME '17:00:00', TIME '18:59:00', 'REGULAR_2D'),
    (34, 6, 13, DATEADD('DAY', 5, CURRENT_DATE), TIME '21:00:00', TIME '22:59:00', 'REGULAR_3D'),
    (35, 6, 2, DATEADD('DAY', 6, CURRENT_DATE), TIME '19:30:00', TIME '21:29:00', 'REGULAR_2D'),
    (36, 6, 8, DATEADD('DAY', 7, CURRENT_DATE), TIME '20:00:00', TIME '21:59:00', 'REGULAR_3D'),

-- Minions
    (37, 7, 1, DATEADD('DAY', 1, CURRENT_DATE), TIME '10:20:00', TIME '11:51:00', 'REGULAR_2D'),
    (38, 7, 6, DATEADD('DAY', 2, CURRENT_DATE), TIME '13:40:00', TIME '15:11:00', 'REGULAR_3D'),
    (39, 7, 11, DATEADD('DAY', 3, CURRENT_DATE), TIME '11:00:00', TIME '12:31:00', 'REGULAR_2D'),
    (40, 7, 11, DATEADD('DAY', 4, CURRENT_DATE), TIME '16:10:00', TIME '17:41:00', 'REGULAR_3D'),
    (41, 7, 3, DATEADD('DAY', 5, CURRENT_DATE), TIME '14:30:00', TIME '16:01:00', 'REGULAR_2D'),
    (42, 7, 1, DATEADD('DAY', 6, CURRENT_DATE), TIME '10:00:00', TIME '11:31:00', 'REGULAR_3D'),

-- Zootopia 2
    (43, 8, 2, DATEADD('DAY', 1, CURRENT_DATE), TIME '11:30:00', TIME '13:18:00', 'REGULAR_2D'),
    (44, 8, 15, DATEADD('DAY', 2, CURRENT_DATE), TIME '15:20:00', TIME '17:08:00', 'REGULAR_3D'),
    (45, 8, 7, DATEADD('DAY', 3, CURRENT_DATE), TIME '18:30:00', TIME '20:18:00', 'REGULAR_2D'),
    (46, 8, 4, DATEADD('DAY', 4, CURRENT_DATE), TIME '20:30:00', TIME '22:18:00', 'REGULAR_3D'),
    (47, 8, 13, DATEADD('DAY', 5, CURRENT_DATE), TIME '16:00:00', TIME '17:48:00', 'REGULAR_2D'),
    (48, 8, 3, DATEADD('DAY', 6, CURRENT_DATE), TIME '19:00:00', TIME '20:48:00', 'REGULAR_3D');

ALTER TABLE screenings ALTER COLUMN id RESTART WITH 100;
