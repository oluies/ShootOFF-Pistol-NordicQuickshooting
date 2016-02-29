/*
 * ShootOFF - Software for Laser Dry Fire Training
 * Nordic Quick Shot event plugin
 * Copyright (C) 2015 phrack
 * Copyright (C) 2015 oluies@github

 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.shootoff.plugins;

import com.shootoff.camera.Shot;
import com.shootoff.gui.Hit;
import javafx.scene.Group;
import javafx.scene.paint.Color;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.*;



import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;



public class NordicQuickShooting extends TrainingExerciseBase implements TrainingExercise {

	private final static String SCORE_COL_NAME = "Score";
	private final static int SCORE_COL_WIDTH = 60;
	private final static String ROUND_COL_NAME = "Round";
	private final static int ROUND_COL_WIDTH = 80;
	private final static int START_DELAY = 10; // s
	private static final int CORE_POOL_SIZE = 4;
	private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE);
	private ScheduledFuture<Void> endRound;
	private TrainingExerciseBase api;
	private int round = 1;
	private int shotCount = 0;
	private int runningScore = 0;
	private int showDelay = 7;
	private int roundTime = 3;
	private boolean repeatExercise = false;
	private boolean coloredRows = false;
	private boolean testing = false;

	public NordicQuickShooting() {}

	public NordicQuickShooting(List<Group> targets) {
		super(targets);
		api = super.getInstance();
		setInitialValues();
	}

	@Override
	public ExerciseMetadata getInfo() {
		return new ExerciseMetadata("Nordic Quick Shot event", "1.0", "oluies",
				"This exercise implements the Nordic Quick Shot event ( Snabbpistol) described at: "
						+ "http://www.pistolskytteforbundet.se/om-pistolskytte/snabbskjutning.  "
						+ " In Nordic region and rules (Sweden, Finland, Norway, Denmark) "
						+ " there is a event type called Snabbpistol earlier \"duel\". "
						+ "One shoots at a target with 5 shots. "
						+ "The target is turned away and is shown after 10 seconds, shown 3, gone 7 and so on (5 times)."
						+ "You can use any scored target with this exercise, but use "
						+ "the ISSF target for the most authentic experience.");
	}


	private void setInitialValues() {
		round = 1;
		shotCount = 0;
		runningScore = 0;
	}

	@Override
	public void init() {
		super.pauseShotDetection(true);

		startExercise();
	}

	// For testing
	protected void init(int delay) {
		this.showDelay = delay;

		testing = true;

		startExercise();
	}

	private void startExercise() {
		super.addShotTimerColumn(SCORE_COL_NAME, SCORE_COL_WIDTH);
		super.addShotTimerColumn(ROUND_COL_NAME, ROUND_COL_WIDTH);

		if (!testing) {
			executorService.schedule(new SetupWait(), showDelay, TimeUnit.SECONDS);
		} else {
			new SetupWait().call();
		}
	}




	private class SetupWait implements Callable<Void> {
		@Override
		public Void call() {

            TrainingExerciseBase.playSound(new File("sounds/voice/shootoff-makeready.wav"));
            if (!testing) {
                executorService.schedule(new StartRound(), START_DELAY, TimeUnit.SECONDS);
            } else {
                new StartRound().call();
            }
			return null;
		}
	}

	private class StartRound implements Callable<Void> {
		@Override
		public Void call() {

            if (coloredRows) {
                api.setShotTimerRowColor(Color.LIGHTGRAY);
            } else {
                api.setShotTimerRowColor(null);
            }

            coloredRows = !coloredRows;

            TrainingExerciseBase.playSound("sounds/beep.wav");
            api.pauseShotDetection(false);
            endRound = executorService.schedule(new EndRound(), roundTime, TimeUnit.SECONDS);

			return null;
		}
	}

	private class EndRound implements Callable<Void> {
		@Override
		public Void call() {

				api.pauseShotDetection(true);
				TrainingExerciseBase.playSound("sounds/chime.wav");


				if (round < 5) {
					// Go to next round
					if (!testing) {
						executorService.schedule(new StartRound(), showDelay, TimeUnit.SECONDS);
					} else {
						new StartRound().call();
					}
				} else {
					TextToSpeech.say("Event over... Your score is " + runningScore);
					api.pauseShotDetection(false);
					// At this point we end and the user has to hit reset to
					// start again
				}

            ++round;
			return null;
		}
	}



	@Override
	public void shotListener(Shot shot, Optional<Hit> hitRegion) {
		shotCount++;

		int hitScore = 0;

		if (hitRegion.isPresent()) {
			Hit hit = hitRegion.get();

			if (hit.getHitRegion().tagExists("points")) {
				hitScore = Integer.parseInt(hit.getHitRegion().getTag("points"));
				runningScore += hitScore;
			}

			StringBuilder message = new StringBuilder();

			super.showTextOnFeed(message.toString() + "total score: " + runningScore);
		}

		String currentRound = String.format("R%d (%ds)", round, showDelay);
		super.setShotTimerColumnText(SCORE_COL_NAME, String.valueOf(hitScore));
		super.setShotTimerColumnText(ROUND_COL_NAME, currentRound);

		if (shotCount == 1 && !endRound.isDone()) {
				api.pauseShotDetection(true);
				endRound.cancel(true);
				new EndRound().call();
		}

	}

	@Override
	public void reset(List<Group> targets) {
		super.pauseShotDetection(true);

		repeatExercise = false;
		executorService.shutdownNow();

		setInitialValues();

		api.setShotTimerRowColor(null);
		super.showTextOnFeed("");

		repeatExercise = true;
		executorService = Executors.newScheduledThreadPool(CORE_POOL_SIZE);
		executorService.schedule(new SetupWait(), START_DELAY, TimeUnit.SECONDS);
	}


	/*
	 * Load a sound file from the exercise's JAR file into a
	 * BufferedInputStream. This specific type of stream is required to play
	 * audio.
	 */
	private InputStream getSoundStream(String soundResource) {
		return new BufferedInputStream(NordicQuickShooting.class.getResourceAsStream(soundResource));
	}



	@Override
	public void destroy() {
		repeatExercise = false;
		executorService.shutdownNow();
		super.destroy();
	}


}

